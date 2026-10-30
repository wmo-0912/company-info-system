package com.example.company.employee.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.company.audit.service.AuditService;
import com.example.company.audit.support.AuditDiff;
import com.example.company.common.api.PageResponse;
import com.example.company.common.enums.EmployeeStatus;
import com.example.company.common.exception.BusinessException;
import com.example.company.common.security.DataScopeService;
import com.example.company.dictionary.service.DictionaryService;
import com.example.company.common.security.LoginUser;
import com.example.company.common.security.SecurityUtils;
import com.example.company.department.entity.Department;
import com.example.company.department.mapper.DepartmentMapper;
import com.example.company.employee.dto.EmployeeCreateRequest;
import com.example.company.employee.dto.EmployeeUpdateRequest;
import com.example.company.employee.entity.Employee;
import com.example.company.employee.mapper.EmployeeMapper;
import com.example.company.employee.service.EmployeeConverter;
import com.example.company.employee.service.EmployeeService;
import com.example.company.employee.vo.EmployeeDetailVO;
import com.example.company.employee.vo.EmployeeListItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeMapper employeeMapper;
    private final DepartmentMapper departmentMapper;
    private final DataScopeService dataScopeService;
    private final AuditService auditService;
    private final DictionaryService dictionaryService;

    @Override
    public PageResponse<EmployeeListItemVO> page(int page, int pageSize, String keyword, Long departmentId, String status) {
        LoginUser current = SecurityUtils.getCurrentUser();
        Set<Long> visible = dataScopeService.visibleEmployeeIds(current);
        if (visible != null && visible.isEmpty()) {
            return PageResponse.of(List.of(), page, pageSize, 0);
        }

        QueryWrapper<Employee> qw = new QueryWrapper<>();
        if (visible != null) {
            qw.in("id", visible);
        }
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like("employee_name", keyword).or().like("employee_no", keyword));
        }
        if (departmentId != null) {
            qw.in("department_id", dataScopeService.expandDepartmentIds(departmentId));
        }
        if (StringUtils.hasText(status)) {
            // 支持逗号分隔多状态，如 "PROBATION,REGULAR"（在职）或 "PENDING"（待入职）
            List<String> statuses = java.util.Arrays.stream(status.split(","))
                    .map(String::trim).filter(StringUtils::hasText).toList();
            if (statuses.size() == 1) {
                qw.eq("employment_status", statuses.get(0));
            } else if (!statuses.isEmpty()) {
                qw.in("employment_status", statuses);
            }
        }
        qw.orderByAsc("employee_no");

        Page<Employee> result = employeeMapper.selectPage(new Page<>(page, pageSize), qw);
        Map<Long, String> deptNames = loadDepartmentNames(result.getRecords());
        return PageResponse.from(result, e -> {
            EmployeeListItemVO vo = EmployeeConverter.toListItem(e);
            vo.setDepartmentName(deptNames.get(e.getDepartmentId()));
            return vo;
        });
    }

    @Override
    public EmployeeDetailVO detail(Long id) {
        Employee employee = requireEmployee(id);
        LoginUser current = SecurityUtils.getCurrentUser();
        if (!dataScopeService.canAccessEmployee(current, id)) {
            throw BusinessException.forbidden("无权查看该员工档案");
        }
        return toDetailVO(employee, canViewSensitive(current, id));
    }

    @Override
    @Transactional
    public EmployeeDetailVO create(EmployeeCreateRequest request) {
        if (employeeMapper.selectCount(new QueryWrapper<Employee>().eq("employee_no", request.getEmployeeNo())) > 0) {
            throw BusinessException.conflict("员工编号已存在: " + request.getEmployeeNo());
        }
        Employee employee = new Employee();
        apply(employee, request);
        employee.setEmploymentStatus(EmployeeStatus.PENDING.name());
        employeeMapper.insert(employee);
        auditService.success("employee:create", "employee", employee.getId(), "创建员工 " + employee.getEmployeeNo());
        return toDetailVO(employee, true);
    }

    @Override
    @Transactional
    public EmployeeDetailVO update(Long id, EmployeeUpdateRequest request) {
        Employee employee = requireEmployee(id);
        if (EmployeeStatus.RESIGNED.name().equals(employee.getEmploymentStatus())) {
            throw BusinessException.unprocessable("已离职员工档案不可编辑");
        }
        // 修改前先取关键字段快照，写库后据此生成字段级变更写进审计摘要
        AuditDiff.Snapshot before = AuditDiff.snapshot(employee);
        employee.setEmployeeName(request.getEmployeeName());
        employee.setGender(request.getGender());
        employee.setBirthDate(request.getBirthDate());
        employee.setPhone(request.getPhone());
        employee.setEmail(request.getEmail());
        employee.setAddress(request.getAddress());
        employee.setEmergencyContactName(request.getEmergencyContactName());
        employee.setEmergencyContactPhone(request.getEmergencyContactPhone());
        employee.setEducationCode(request.getEducationCode());
        // 部门/岗位属于调岗流程的产物：待入职阶段可直接修正，入职后只能通过 TRANSFER 申请变更
        boolean preboarding = EmployeeStatus.PENDING.name().equals(employee.getEmploymentStatus());
        if (request.getDepartmentId() != null) {
            if (!preboarding && !request.getDepartmentId().equals(employee.getDepartmentId())) {
                throw BusinessException.unprocessable("调整部门请提交调岗（TRANSFER）申请，档案编辑不能直接修改部门");
            }
            assertDepartmentActive(request.getDepartmentId());
            employee.setDepartmentId(request.getDepartmentId());
        }
        if (StringUtils.hasText(request.getPositionName())) {
            if (!preboarding && !request.getPositionName().equals(employee.getPositionName())) {
                throw BusinessException.unprocessable("调整岗位请提交调岗（TRANSFER）申请，档案编辑不能直接修改岗位");
            }
            employee.setPositionName(request.getPositionName());
        }
        employee.setPositionLevelCode(request.getPositionLevelCode());
        assertDictionaries(request.getEducationCode(), request.getPositionLevelCode(), request.getGender());
        assertManagerValid(request.getManagerEmployeeId(), id);
        employee.setManagerEmployeeId(request.getManagerEmployeeId());
        employee.setHireDate(request.getHireDate());
        employee.setRemark(request.getRemark());
        employeeMapper.updateById(employee);
        String diff = AuditDiff.describe(before, employee, this::formatAuditValue);
        auditService.success("employee:update", "employee", id,
                "更新员工档案 " + employee.getEmployeeNo() + (diff.isEmpty() ? "" : "；" + diff));
        return toDetailVO(employee, true);
    }

    @Override
    @Transactional
    public void archive(Long id) {
        Employee employee = requireEmployee(id);
        if (!EmployeeStatus.RESIGNED.name().equals(employee.getEmploymentStatus())) {
            throw BusinessException.unprocessable("仅已离职员工可以归档");
        }
        employeeMapper.deleteById(id);
        auditService.success("employee:archive", "employee", id, "归档员工 " + employee.getEmployeeNo());
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

    /** 学历/职级/性别必须取自启用状态的字典项，避免写入 BACHELOR_X 这类脏值 */
    private void assertDictionaries(String educationCode, String positionLevelCode, String gender) {
        dictionaryService.validateDictionaryItem("EDUCATION", educationCode, "学历");
        dictionaryService.validateDictionaryItem("POSITION_LEVEL", positionLevelCode, "职位层级");
        dictionaryService.validateDictionaryItem("GENDER", gender, "性别");
    }

    public Employee requireEmployee(Long id) {
        Employee employee = employeeMapper.selectById(id);
        if (employee == null) {
            throw BusinessException.notFound("员工不存在");
        }
        return employee;
    }

    /** 敏感字段（联系方式、住址、紧急联系人等）仅本人或拥有 employee:read 全量数据权限的角色可见 */
    private boolean canViewSensitive(LoginUser current, Long employeeId) {
        if (employeeId.equals(current.getEmployeeId())) {
            return true;
        }
        return "ALL".equals(current.getDataScope()) || "DEPARTMENT".equals(current.getDataScope());
    }

    private void apply(Employee employee, EmployeeCreateRequest request) {
        employee.setEmployeeNo(request.getEmployeeNo());
        employee.setEmployeeName(request.getEmployeeName());
        employee.setGender(request.getGender());
        employee.setBirthDate(request.getBirthDate());
        employee.setPhone(request.getPhone());
        employee.setEmail(request.getEmail());
        employee.setAddress(request.getAddress());
        employee.setEmergencyContactName(request.getEmergencyContactName());
        employee.setEmergencyContactPhone(request.getEmergencyContactPhone());
        employee.setEducationCode(request.getEducationCode());
        assertDepartmentActive(request.getDepartmentId());
        employee.setDepartmentId(request.getDepartmentId());
        employee.setPositionName(request.getPositionName());
        employee.setPositionLevelCode(request.getPositionLevelCode());
        assertDictionaries(request.getEducationCode(), request.getPositionLevelCode(), request.getGender());
        assertManagerValid(request.getManagerEmployeeId(), null);
        employee.setManagerEmployeeId(request.getManagerEmployeeId());
        employee.setHireDate(request.getHireDate());
        employee.setRemark(request.getRemark());
    }

    private void assertDepartmentActive(Long departmentId) {
        Department dept = departmentMapper.selectById(departmentId);
        if (dept == null || !"ACTIVE".equals(dept.getStatus())) {
            throw BusinessException.badRequest("部门不存在或已停用");
        }
    }

    private void assertManagerValid(Long managerId, Long selfId) {
        if (managerId == null) {
            return;
        }
        if (managerId.equals(selfId)) {
            throw BusinessException.badRequest("主管不能是员工本人");
        }
        if (employeeMapper.selectById(managerId) == null) {
            throw BusinessException.badRequest("主管员工不存在");
        }
    }

    private EmployeeDetailVO toDetailVO(Employee e, boolean includeSensitive) {
        Department dept = e.getDepartmentId() == null ? null : departmentMapper.selectById(e.getDepartmentId());
        Employee manager = e.getManagerEmployeeId() == null ? null : employeeMapper.selectById(e.getManagerEmployeeId());
        return EmployeeConverter.toDetail(e, dept, manager, includeSensitive);
    }

    private Map<Long, String> loadDepartmentNames(List<Employee> employees) {
        Set<Long> deptIds = employees.stream().map(Employee::getDepartmentId).collect(Collectors.toSet());
        if (deptIds.isEmpty()) {
            return Map.of();
        }
        return departmentMapper.selectBatchIds(deptIds).stream()
                .collect(Collectors.toMap(Department::getId, Department::getDepartmentName));
    }
}
