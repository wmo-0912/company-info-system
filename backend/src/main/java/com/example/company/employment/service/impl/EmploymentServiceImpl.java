package com.example.company.employment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.company.common.enums.DataModule;
import com.example.company.audit.service.AuditService;
import com.example.company.audit.support.AuditDiff;
import com.example.company.common.api.PageResponse;
import com.example.company.common.enums.ChangeType;
import com.example.company.common.enums.EmployeeStatus;
import com.example.company.common.enums.RequestStatus;
import com.example.company.common.exception.BusinessException;
import com.example.company.common.security.DataScopeService;
import com.example.company.common.security.LoginUser;
import com.example.company.common.security.SecurityUtils;
import com.example.company.department.entity.Department;
import com.example.company.department.mapper.DepartmentMapper;
import com.example.company.employee.entity.Employee;
import com.example.company.employee.mapper.EmployeeMapper;
import com.example.company.employment.dto.ChangeRequestCreateDTO;
import com.example.company.employment.dto.ChangeRequestProcessDTO;
import com.example.company.employment.entity.EmployeeChangeRequest;
import com.example.company.employment.entity.EmployeeEmploymentRecord;
import com.example.company.employment.mapper.EmployeeChangeRequestMapper;
import com.example.company.employment.mapper.EmployeeEmploymentRecordMapper;
import com.example.company.employment.service.EmploymentService;
import com.example.company.employment.vo.ChangeRequestVO;
import com.example.company.employment.vo.EmploymentRecordVO;
import com.example.company.project.entity.ProjectTeamMember;
import com.example.company.project.mapper.ProjectTeamMemberMapper;
import com.example.company.user.entity.SysUser;
import com.example.company.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmploymentServiceImpl implements EmploymentService {

    private final EmployeeChangeRequestMapper requestMapper;
    private final EmployeeEmploymentRecordMapper recordMapper;
    private final EmployeeMapper employeeMapper;
    private final DepartmentMapper departmentMapper;
    private final ProjectTeamMemberMapper projectMemberMapper;
    private final SysUserMapper userMapper;
    private final DataScopeService dataScopeService;
    private final AuditService auditService;

    @Override
    public PageResponse<ChangeRequestVO> page(int page, int pageSize, String status, String changeType, Long employeeId) {
        LoginUser current = SecurityUtils.getCurrentUser();
        Set<Long> visible = dataScopeService.visibleEmployeeIds(current, DataModule.EMPLOYMENT);
        if (visible != null && visible.isEmpty()) {
            return PageResponse.of(List.of(), page, pageSize, 0);
        }
        QueryWrapper<EmployeeChangeRequest> qw = new QueryWrapper<>();
        if (visible != null) {
            qw.in("employee_id", visible);
        }
        if (StringUtils.hasText(status)) {
            qw.eq("status", status);
        }
        if (StringUtils.hasText(changeType)) {
            qw.eq("change_type", changeType);
        }
        if (employeeId != null) {
            qw.eq("employee_id", employeeId);
        }
        qw.orderByDesc("created_at");
        Page<EmployeeChangeRequest> result = requestMapper.selectPage(new Page<>(page, pageSize), qw);
        return PageResponse.from(result, this::toVO);
    }

    @Override
    public ChangeRequestVO detail(Long id) {
        EmployeeChangeRequest req = requireRequest(id);
        assertEmployeeVisible(req.getEmployeeId());
        return toVO(req);
    }

    /**
     * 创建申请。不使用事务包装：申请号在高并发下仍可能撞唯一键，重试需要每条 insert 各自独立提交
     * （PostgreSQL 同一事务里一旦违反唯一约束就会进入 aborted 状态，同事务重试必然继续失败）。
     */
    @Override
    public ChangeRequestVO create(ChangeRequestCreateDTO dto) {
        ChangeType changeType = parseChangeType(dto.getChangeType());
        Employee employee = requireEmployee(dto.getEmployeeId());
        validateRequestForEmployee(changeType, employee, dto);

        // 同一员工不允许存在进行中的同类型请求
        Long inFlight = requestMapper.selectCount(new QueryWrapper<EmployeeChangeRequest>()
                .eq("employee_id", employee.getId())
                .eq("change_type", changeType.name())
                .in("status", RequestStatus.DRAFT.name(), RequestStatus.SUBMITTED.name()));
        if (inFlight > 0) {
            throw BusinessException.conflict("该员工存在进行中的同类型申请");
        }

        EmployeeChangeRequest req = new EmployeeChangeRequest();
        req.setEmployeeId(employee.getId());
        req.setChangeType(changeType.name());
        req.setStatus(RequestStatus.DRAFT.name());
        req.setEffectiveDate(dto.getEffectiveDate());
        req.setReason(dto.getReason());
        req.setPayload(dto.getPayload() == null ? Map.of() : dto.getPayload());
        req.setSubmittedBy(SecurityUtils.getCurrentUserId());
        insertWithUniqueRequestNo(req);
        auditService.success("employment:create", "change_request", req.getId(),
                "创建" + changeType.name() + "申请，员工ID=" + employee.getId());
        return toVO(req);
    }

    @Override
    @Transactional
    public ChangeRequestVO submit(Long id) {
        EmployeeChangeRequest req = requireRequest(id);
        if (!RequestStatus.DRAFT.name().equals(req.getStatus())) {
            throw BusinessException.unprocessable("仅草稿状态的申请可以提交");
        }
        req.setStatus(RequestStatus.SUBMITTED.name());
        requestMapper.updateById(req);
        auditService.success("employment:submit", "change_request", id, "提交申请 " + req.getRequestNo());
        return toVO(req);
    }

    @Override
    @Transactional
    public ChangeRequestVO process(Long id, ChangeRequestProcessDTO dto) {
        EmployeeChangeRequest req = requireRequest(id);
        if (!RequestStatus.SUBMITTED.name().equals(req.getStatus())) {
            throw BusinessException.unprocessable("仅已提交状态的申请可以办理");
        }
        String decision = dto.getDecision();
        if (!"APPROVE".equals(decision) && !"REJECT".equals(decision)) {
            throw BusinessException.badRequest("处理决定必须是 APPROVE 或 REJECT");
        }

        LoginUser current = SecurityUtils.getCurrentUser();
        if (current.getEmployeeId() != null && current.getEmployeeId().equals(req.getEmployeeId())) {
            throw BusinessException.forbidden("不能办理与本人相关的入转调离申请，请由其他有权限的同事办理");
        }
        Long processorId = SecurityUtils.getCurrentUserId();
        req.setProcessedBy(processorId);
        req.setProcessedAt(OffsetDateTime.now());
        req.setProcessComment(dto.getComment());

        if ("REJECT".equals(decision)) {
            req.setStatus(RequestStatus.REJECTED.name());
            requestMapper.updateById(req);
            auditService.success("employment:reject", "change_request", id, "驳回申请 " + req.getRequestNo());
            return toVO(req);
        }

        // 批准的生效日期必须已到：未到期就改主档会让"提前生效"变成既成事实，无法回滚
        if (req.getEffectiveDate() != null && req.getEffectiveDate().isAfter(LocalDate.now())) {
            throw BusinessException.unprocessable("生效日期 " + req.getEffectiveDate()
                    + " 尚未到达，请在当天或之后办理；如需变更请驳回归还申请人修改");
        }

        // 批准：事务内更新员工主档 + 写入任职历史 + 更新申请状态
        Employee employee = requireEmployee(req.getEmployeeId());
        // 主档更新前取快照，批准后据此生成字段级变更（部门/岗位/状态/日期）
        AuditDiff.Snapshot before = AuditDiff.snapshot(employee);
        applyApproval(req, employee);
        employeeMapper.updateById(employee);
        String resignDetail = ChangeType.RESIGN.name().equals(req.getChangeType())
                ? cascadeResignation(employee, req.getEffectiveDate())
                : null;

        EmployeeEmploymentRecord record = new EmployeeEmploymentRecord();
        record.setEmployeeId(employee.getId());
        record.setChangeType(req.getChangeType());
        record.setEffectiveDate(req.getEffectiveDate());
        record.setReason(req.getReason());
        record.setOperatorUserId(processorId);
        fillRecordFromPayload(record, req, employee);
        recordMapper.insert(record);

        req.setStatus(RequestStatus.APPROVED.name());
        requestMapper.updateById(req);
        String employeeDiff = AuditDiff.describe(before, employee, this::formatAuditValue);
        auditService.success("employment:approve", "change_request", id,
                "批准申请 " + req.getRequestNo()
                        + (employeeDiff.isEmpty() ? "" : "；" + employeeDiff)
                        + (resignDetail == null ? "" : "；" + resignDetail));
        return toVO(req);
    }

    private void fillRecordFromPayload(EmployeeEmploymentRecord record, EmployeeChangeRequest req, Employee beforeEmployee) {
        Map<String, Object> snapshot = (Map<String, Object>) req.getPayload().get("snapshot");
        if (snapshot != null) {
            Object oldDept = snapshot.get("oldDepartmentId");
            Object oldPos = snapshot.get("oldPositionName");
            record.setOldDepartmentId(oldDept == null ? null : Long.valueOf(String.valueOf(oldDept)));
            record.setOldPositionName(oldPos == null ? null : String.valueOf(oldPos));
        }
        record.setNewDepartmentId(beforeEmployee.getDepartmentId());
        record.setNewPositionName(beforeEmployee.getPositionName());
    }

    @Override
    @Transactional
    public ChangeRequestVO cancel(Long id) {
        EmployeeChangeRequest req = requireRequest(id);
        if (!RequestStatus.DRAFT.name().equals(req.getStatus()) && !RequestStatus.SUBMITTED.name().equals(req.getStatus())) {
            throw BusinessException.unprocessable("仅草稿或已提交状态的申请可以取消");
        }
        req.setStatus(RequestStatus.CANCELLED.name());
        requestMapper.updateById(req);
        auditService.success("employment:cancel", "change_request", id, "取消申请 " + req.getRequestNo());
        return toVO(req);
    }

    @Override
    public List<EmploymentRecordVO> recordsOfEmployee(Long employeeId) {
        assertEmployeeVisible(employeeId);
        List<EmployeeEmploymentRecord> records = recordMapper.selectList(new QueryWrapper<EmployeeEmploymentRecord>()
                .eq("employee_id", employeeId)
                .orderByDesc("effective_date"));
        return records.stream().map(this::toRecordVO).toList();
    }

    @Override
    public PageResponse<ChangeRequestVO> requestsOfEmployee(Long employeeId, int page, int pageSize) {
        assertEmployeeVisible(employeeId);
        Page<EmployeeChangeRequest> result = requestMapper.selectPage(new Page<>(page, pageSize),
                new QueryWrapper<EmployeeChangeRequest>()
                        .eq("employee_id", employeeId)
                        .orderByDesc("created_at"));
        return PageResponse.from(result, this::toVO);
    }

    // ==================== 内部规则 ====================

    /** 在职（可调岗、可离职）的两种状态：试用期与正式 */
    private boolean isEmployed(String status) {
        return EmployeeStatus.PROBATION.name().equals(status) || EmployeeStatus.REGULAR.name().equals(status);
    }

    /** 申请附加信息只允许这些键（其余一律拒绝，避免脏键静默写进 JSONB） */
    private static final Set<String> TRANSFER_PAYLOAD_KEYS = Set.of("targetDepartmentId", "targetPositionName");
    /** 批准时回写的内部键，校验时放行 */
    private static final Set<String> INTERNAL_PAYLOAD_KEYS = Set.of("snapshot");
    private static final int MAX_POSITION_NAME_LENGTH = 100;

    /**
     * payload 是 JSONB，没有 schema 约束；这里按申请类型做白名单与取值校验，
     * 避免"前端漏传/传错键"变成一条看起来正常、实际什么都没改的申请。
     */
    private void validatePayload(ChangeType type, Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return;
        }
        Set<String> allowed = type == ChangeType.TRANSFER ? TRANSFER_PAYLOAD_KEYS : Set.of();
        for (String key : payload.keySet()) {
            if (INTERNAL_PAYLOAD_KEYS.contains(key)) {
                continue;
            }
            if (!allowed.contains(key)) {
                throw BusinessException.badRequest("申请附加信息包含不支持的字段：" + key);
            }
        }
        Object targetPosition = payload.get("targetPositionName");
        if (targetPosition != null) {
            String name = String.valueOf(targetPosition).trim();
            if (name.isEmpty() || name.length() > MAX_POSITION_NAME_LENGTH) {
                throw BusinessException.badRequest("目标岗位名称不能为空且不超过 " + MAX_POSITION_NAME_LENGTH + " 个字符");
            }
        }
    }

    private void validateRequestForEmployee(ChangeType type, Employee employee, ChangeRequestCreateDTO dto) {
        String status = employee.getEmploymentStatus();
        validatePayload(type, dto.getPayload());
        switch (type) {
            case ONBOARD -> {
                if (!EmployeeStatus.PENDING.name().equals(status)) {
                    throw BusinessException.unprocessable("仅待入职（PENDING）状态的员工可以办理入职");
                }
            }
            case REGULARIZE -> {
                if (!EmployeeStatus.PROBATION.name().equals(status)) {
                    throw BusinessException.unprocessable("仅试用期（PROBATION）状态的员工可以办理转正");
                }
            }
            case TRANSFER -> {
                if (!isEmployed(status)) {
                    throw BusinessException.unprocessable("当前员工状态不允许调岗");
                }
                Long targetDeptId = extractTargetDepartmentId(dto);
                if (targetDeptId == null) {
                    throw BusinessException.badRequest("调岗申请必须提供目标部门 targetDepartmentId");
                }
                Department target = departmentMapper.selectById(targetDeptId);
                if (target == null || !"ACTIVE".equals(target.getStatus())) {
                    throw BusinessException.badRequest("目标部门不存在或已停用");
                }
                if (targetDeptId.equals(employee.getDepartmentId())) {
                    throw BusinessException.unprocessable("目标部门与当前部门相同");
                }
            }
            case RESIGN -> {
                if (!isEmployed(status)) {
                    throw BusinessException.unprocessable("当前员工状态不允许办理离职");
                }
                if (dto.getEffectiveDate().isBefore(java.time.LocalDate.now().minusYears(5))) {
                    throw BusinessException.badRequest("离职日期不合理");
                }
            }
        }
    }

    /** 批准生效：变更员工主档（在同一事务内调用） */
    private void applyApproval(EmployeeChangeRequest req, Employee employee) {
        ChangeType type = ChangeType.valueOf(req.getChangeType());
        // 批准时再次校验状态，防止提交后员工状态被其他流程改变
        validateRequestForEmployee(type, employee, toDTO(req));

        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("oldDepartmentId", employee.getDepartmentId());
        snapshot.put("oldPositionName", employee.getPositionName());

        switch (type) {
            case ONBOARD -> {
                employee.setEmploymentStatus(EmployeeStatus.PROBATION.name());
                employee.setHireDate(req.getEffectiveDate());
            }
            case REGULARIZE -> employee.setEmploymentStatus(EmployeeStatus.REGULAR.name());
            case TRANSFER -> {
                employee.setDepartmentId(extractTargetDepartmentId(toDTO(req)));
                Object targetPos = req.getPayload().get("targetPositionName");
                if (targetPos != null && StringUtils.hasText(String.valueOf(targetPos))) {
                    employee.setPositionName(String.valueOf(targetPos));
                }
                // 调岗只改部门与岗位，在职状态保持不变：试用期调岗仍是 PROBATION，转正后调岗仍是 REGULAR，
                // 否则"已调岗"这种一次性事件会被写成终身状态，后续转正/离职判断全部失效。
            }
            case RESIGN -> {
                employee.setEmploymentStatus(EmployeeStatus.RESIGNED.name());
                employee.setResignationDate(req.getEffectiveDate());
            }
        }
        req.getPayload().put("snapshot", snapshot);
    }

    /** 审计变更的可读化：部门与直属主管显示名称而不是内部 ID */
    private String formatAuditValue(String field, Object value) {
        if ("departmentId".equals(field)) {
            Department dept = departmentMapper.selectById((Long) value);
            return dept == null ? null : dept.getDepartmentName();
        }
        if ("managerEmployeeId".equals(field)) {
            Employee manager = employeeMapper.selectById((Long) value);
            return manager == null ? null : manager.getEmployeeName();
        }
        return null;
    }

    /**
     * 离职批准后的级联收尾（与主档更新同一事务）：
     * 1. 停用该员工关联的登录账号——JWT 过滤器每次请求都会重新加载账号状态，停用后旧 token 立即失效；
     * 2. 结束进行中的项目组成员关系（写 left_at），避免数据范围仍把离职者算作有效成员；
     * 3. 清空指向该员工的部门负责人引用，避免"幽灵负责人"。
     *
     * @return 供审计记录使用的摘要
     */
    private String cascadeResignation(Employee employee, LocalDate effectiveDate) {
        LocalDate leftAt = effectiveDate == null ? LocalDate.now() : effectiveDate;
        int disabledUsers = userMapper.update(null, new UpdateWrapper<SysUser>()
                .set("status", "DISABLED")
                .eq("employee_id", employee.getId())
                .eq("status", "ACTIVE"));
        int closedMemberships = projectMemberMapper.update(null, new UpdateWrapper<ProjectTeamMember>()
                .set("left_at", leftAt)
                .eq("employee_id", employee.getId())
                .isNull("left_at"));
        int clearedManagers = departmentMapper.update(null, new UpdateWrapper<Department>()
                .setSql("manager_employee_id = NULL")
                .eq("manager_employee_id", employee.getId()));
        return "停用账号 " + disabledUsers + " 个、结束项目组成员关系 " + closedMemberships
                + " 个、清理部门负责人 " + clearedManagers + " 处";
    }

    private ChangeRequestCreateDTO toDTO(EmployeeChangeRequest req) {
        ChangeRequestCreateDTO dto = new ChangeRequestCreateDTO();
        dto.setEmployeeId(req.getEmployeeId());
        dto.setChangeType(req.getChangeType());
        dto.setEffectiveDate(req.getEffectiveDate());
        dto.setReason(req.getReason());
        dto.setPayload(req.getPayload());
        return dto;
    }

    private Long extractTargetDepartmentId(ChangeRequestCreateDTO dto) {
        if (dto.getPayload() == null) {
            return null;
        }
        Object v = dto.getPayload().get("targetDepartmentId");
        if (v == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(v));
        } catch (NumberFormatException e) {
            throw BusinessException.badRequest("目标部门 ID 格式不正确");
        }
    }

    private void assertEmployeeVisible(Long employeeId) {
        if (!dataScopeService.canAccessEmployee(SecurityUtils.getCurrentUser(), employeeId, DataModule.EMPLOYMENT)) {
            throw BusinessException.forbidden("无权访问该员工相关数据");
        }
    }

    private Employee requireEmployee(Long id) {
        Employee employee = employeeMapper.selectById(id);
        if (employee == null) {
            throw BusinessException.notFound("员工不存在");
        }
        return employee;
    }

    private EmployeeChangeRequest requireRequest(Long id) {
        EmployeeChangeRequest req = requestMapper.selectById(id);
        if (req == null) {
            throw BusinessException.notFound("申请不存在");
        }
        return req;
    }

    private ChangeType parseChangeType(String value) {
        try {
            return ChangeType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw BusinessException.badRequest("非法的变更类型: " + value);
        }
    }

    /**
     * 申请号：CR + 毫秒级时间戳 + 6 位随机数，共 23 字符（列宽 40）。
     * 毫秒 + 6 位随机把同毫秒碰撞概率压到百万分之一以下，仍保留唯一键兜底并配合重试。
     */
    private String generateRequestNo() {
        return "CR" + OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
    }

    private static final int REQUEST_NO_ATTEMPTS = 3;

    /** 插入申请，唯一键冲突（申请号重复）时重新取号重试，最多 {@value #REQUEST_NO_ATTEMPTS} 次 */
    private void insertWithUniqueRequestNo(EmployeeChangeRequest req) {
        for (int attempt = 1; ; attempt++) {
            req.setRequestNo(generateRequestNo());
            try {
                requestMapper.insert(req);
                return;
            } catch (DuplicateKeyException e) {
                if (attempt >= REQUEST_NO_ATTEMPTS) {
                    throw BusinessException.conflict("申请号生成冲突，请稍后重试");
                }
                req.setId(null);
            }
        }
    }

    private ChangeRequestVO toVO(EmployeeChangeRequest req) {
        ChangeRequestVO vo = new ChangeRequestVO();
        vo.setId(req.getId());
        vo.setRequestNo(req.getRequestNo());
        vo.setEmployeeId(req.getEmployeeId());
        Employee employee = employeeMapper.selectById(req.getEmployeeId());
        if (employee != null) {
            vo.setEmployeeName(employee.getEmployeeName());
            vo.setEmployeeNo(employee.getEmployeeNo());
        }
        vo.setChangeType(req.getChangeType());
        vo.setStatus(req.getStatus());
        vo.setEffectiveDate(req.getEffectiveDate());
        vo.setPayload(req.getPayload());
        vo.setReason(req.getReason());
        vo.setSubmittedBy(req.getSubmittedBy());
        vo.setSubmittedByName(userName(req.getSubmittedBy()));
        vo.setProcessedBy(req.getProcessedBy());
        vo.setProcessedByName(userName(req.getProcessedBy()));
        vo.setProcessedAt(req.getProcessedAt());
        vo.setProcessComment(req.getProcessComment());
        vo.setCreatedAt(req.getCreatedAt());
        return vo;
    }

    private String userName(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = userMapper.selectById(userId);
        return user == null ? null : user.getUsername();
    }

    private EmploymentRecordVO toRecordVO(EmployeeEmploymentRecord r) {
        EmploymentRecordVO vo = new EmploymentRecordVO();
        vo.setId(r.getId());
        vo.setEmployeeId(r.getEmployeeId());
        vo.setChangeType(r.getChangeType());
        vo.setEffectiveDate(r.getEffectiveDate());
        vo.setOldDepartmentId(r.getOldDepartmentId());
        vo.setOldDepartmentName(deptName(r.getOldDepartmentId()));
        vo.setNewDepartmentId(r.getNewDepartmentId());
        vo.setNewDepartmentName(deptName(r.getNewDepartmentId()));
        vo.setOldPositionName(r.getOldPositionName());
        vo.setNewPositionName(r.getNewPositionName());
        vo.setReason(r.getReason());
        vo.setOperatorName(userName(r.getOperatorUserId()));
        return vo;
    }

    private String deptName(Long deptId) {
        if (deptId == null) {
            return null;
        }
        Department dept = departmentMapper.selectById(deptId);
        return dept == null ? null : dept.getDepartmentName();
    }
}
