package com.example.company.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.company.common.enums.DataModule;
import com.example.company.audit.service.AuditService;
import com.example.company.common.api.PageResponse;
import com.example.company.common.enums.EmployeeStatus;
import com.example.company.common.exception.BusinessException;
import com.example.company.common.security.DataScopeService;
import com.example.company.common.security.LoginUser;
import com.example.company.common.security.SecurityUtils;
import com.example.company.department.entity.Department;
import com.example.company.department.mapper.DepartmentMapper;
import com.example.company.employee.entity.Employee;
import com.example.company.employee.mapper.EmployeeMapper;
import com.example.company.project.dto.ProjectMemberRequest;
import com.example.company.project.dto.ProjectRequest;
import com.example.company.project.entity.ProjectTeam;
import com.example.company.project.entity.ProjectTeamMember;
import com.example.company.project.mapper.ProjectTeamMapper;
import com.example.company.project.mapper.ProjectTeamMemberMapper;
import com.example.company.project.service.ProjectService;
import com.example.company.project.vo.ProjectMemberVO;
import com.example.company.project.vo.ProjectVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectTeamMapper teamMapper;
    private final ProjectTeamMemberMapper memberMapper;
    private final EmployeeMapper employeeMapper;
    private final DepartmentMapper departmentMapper;
    private final DataScopeService dataScopeService;
    private final AuditService auditService;

    @Override
    public PageResponse<ProjectVO> page(int page, int pageSize, String keyword, String status) {
        LoginUser current = SecurityUtils.getCurrentUser();
        QueryWrapper<ProjectTeam> qw = new QueryWrapper<>();
        // PROJECT 数据范围：仅自己负责或自己参与的项目组
        if ("PROJECT".equals(dataScopeService.effectiveScope(current, DataModule.PROJECT)) && current.getEmployeeId() != null) {
            List<Long> memberTeamIds = memberMapper.selectList(new QueryWrapper<ProjectTeamMember>()
                            .eq("employee_id", current.getEmployeeId()).isNull("left_at"))
                    .stream().map(ProjectTeamMember::getProjectTeamId).toList();
            qw.and(w -> {
                w.eq("manager_employee_id", current.getEmployeeId());
                if (!memberTeamIds.isEmpty()) {
                    w.or().in("id", memberTeamIds);
                }
            });
        } else if ("SELF".equals(dataScopeService.effectiveScope(current, DataModule.PROJECT))) {
            if (current.getEmployeeId() == null) {
                return PageResponse.of(List.of(), page, pageSize, 0);
            }
            List<Long> memberTeamIds = memberMapper.selectList(new QueryWrapper<ProjectTeamMember>()
                            .eq("employee_id", current.getEmployeeId()).isNull("left_at"))
                    .stream().map(ProjectTeamMember::getProjectTeamId).toList();
            if (memberTeamIds.isEmpty()) {
                return PageResponse.of(List.of(), page, pageSize, 0);
            }
            qw.in("id", memberTeamIds);
        }
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like("project_name", keyword).or().like("project_code", keyword));
        }
        if (StringUtils.hasText(status)) {
            qw.eq("status", status);
        }
        qw.orderByAsc("project_code");
        Page<ProjectTeam> result = teamMapper.selectPage(new Page<>(page, pageSize), qw);
        return PageResponse.of(toVOs(result.getRecords()), result.getCurrent(), result.getSize(), result.getTotal());
    }

    @Override
    public ProjectVO detail(Long id) {
        ProjectTeam team = requireTeam(id);
        assertVisible(team);
        return toVO(team);
    }

    @Override
    @Transactional
    public ProjectVO create(ProjectRequest request) {
        assertCodeUnique(request.getProjectCode(), null);
        assertManagerValid(request.getManagerEmployeeId());
        ProjectTeam team = new ProjectTeam();
        team.setProjectCode(request.getProjectCode());
        team.setProjectName(request.getProjectName());
        team.setManagerEmployeeId(request.getManagerEmployeeId());
        team.setDescription(request.getDescription());
        team.setStatus("ACTIVE");
        teamMapper.insert(team);
        auditService.success("project:create", "project", team.getId(), "创建项目组 " + team.getProjectName());
        return toVO(team);
    }

    @Override
    @Transactional
    public ProjectVO update(Long id, ProjectRequest request) {
        ProjectTeam team = requireTeam(id);
        assertManageable(team);
        assertCodeUnique(request.getProjectCode(), id);
        assertManagerValid(request.getManagerEmployeeId());
        team.setProjectCode(request.getProjectCode());
        team.setProjectName(request.getProjectName());
        team.setManagerEmployeeId(request.getManagerEmployeeId());
        team.setDescription(request.getDescription());
        teamMapper.updateById(team);
        auditService.success("project:update", "project", id, "更新项目组 " + team.getProjectName());
        return toVO(team);
    }

    @Override
    @Transactional
    public ProjectVO updateStatus(Long id, String status) {
        ProjectTeam team = requireTeam(id);
        assertManageable(team);
        if (!"ACTIVE".equals(status) && !"CLOSED".equals(status)) {
            throw BusinessException.badRequest("状态只能是 ACTIVE 或 CLOSED");
        }
        if (status.equals(team.getStatus())) {
            return toVO(team);
        }
        team.setStatus(status);
        teamMapper.updateById(team);
        auditService.success("project:status", "project", id,
                ("CLOSED".equals(status) ? "关闭项目组 " : "重新开启项目组 ") + team.getProjectName());
        return toVO(team);
    }

    @Override
    public List<ProjectMemberVO> members(Long id) {
        ProjectTeam team = requireTeam(id);
        assertVisible(team);
        List<ProjectTeamMember> members = memberMapper.selectList(new QueryWrapper<ProjectTeamMember>()
                .eq("project_team_id", id).orderByAsc("joined_at"));
        return toMemberVOs(members);
    }

    @Override
    @Transactional
    public ProjectMemberVO addMember(Long id, ProjectMemberRequest request) {
        ProjectTeam team = requireTeam(id);
        assertManageable(team);
        if (!"ACTIVE".equals(team.getStatus())) {
            throw BusinessException.unprocessable("已关闭的项目组不能新增成员");
        }
        Employee employee = employeeMapper.selectById(request.getEmployeeId());
        if (employee == null || EmployeeStatus.RESIGNED.name().equals(employee.getEmploymentStatus())) {
            throw BusinessException.badRequest("员工不存在或已离职");
        }
        ProjectTeamMember member = new ProjectTeamMember();
        member.setProjectTeamId(id);
        member.setEmployeeId(request.getEmployeeId());
        member.setMemberRole(request.getMemberRole());
        member.setJoinedAt(request.getJoinedAt() == null ? LocalDate.now() : request.getJoinedAt());
        try {
            memberMapper.insert(member);
        } catch (DuplicateKeyException e) {
            throw BusinessException.conflict("该员工已是项目组成员");
        }
        auditService.success("project:member:add", "project", id, "新增成员员工ID=" + request.getEmployeeId());
        return toMemberVO(member);
    }

    @Override
    @Transactional
    public void removeMember(Long id, Long employeeId, LocalDate leftAt) {
        ProjectTeam team = requireTeam(id);
        assertManageable(team);
        ProjectTeamMember member = memberMapper.selectOne(new QueryWrapper<ProjectTeamMember>()
                .eq("project_team_id", id).eq("employee_id", employeeId).isNull("left_at"));
        if (member == null) {
            throw BusinessException.notFound("该员工不是项目组有效成员");
        }
        LocalDate leaveDate = leftAt == null ? LocalDate.now() : leftAt;
        if (member.getJoinedAt() != null && leaveDate.isBefore(member.getJoinedAt())) {
            throw BusinessException.badRequest("离开日期不能早于加入日期 " + member.getJoinedAt());
        }
        member.setLeftAt(leaveDate);
        memberMapper.updateById(member);
        auditService.success("project:member:remove", "project", id,
                "移除成员员工ID=" + employeeId + "，离开日期 " + leaveDate);
    }

    @Override
    public List<ProjectVO> myTeams() {
        LoginUser current = SecurityUtils.getCurrentUser();
        if (current.getEmployeeId() == null) {
            return List.of();
        }
        List<ProjectTeam> managed = teamMapper.selectList(new QueryWrapper<ProjectTeam>()
                .eq("manager_employee_id", current.getEmployeeId()));
        List<Long> memberTeamIds = memberMapper.selectList(new QueryWrapper<ProjectTeamMember>()
                        .eq("employee_id", current.getEmployeeId()).isNull("left_at"))
                .stream().map(ProjectTeamMember::getProjectTeamId).toList();
        List<ProjectTeam> joined = memberTeamIds.isEmpty() ? List.of()
                : teamMapper.selectBatchIds(memberTeamIds);
        Map<Long, ProjectTeam> all = new java.util.LinkedHashMap<>();
        managed.forEach(t -> all.put(t.getId(), t));
        joined.forEach(t -> all.put(t.getId(), t));
        return toVOs(new ArrayList<>(all.values()));
    }

    /** 读操作校验：项目组必须在当前用户数据范围内，避免按 ID 直查越权 */
    private void assertVisible(ProjectTeam team) {
        if (!dataScopeService.canAccessProject(SecurityUtils.getCurrentUser(), team, DataModule.PROJECT)) {
            throw BusinessException.forbidden("无权查看该项目组");
        }
    }

    /** 管理操作校验：拥有全局权限或本人是负责人 */
    private void assertManageable(ProjectTeam team) {
        LoginUser current = SecurityUtils.getCurrentUser();
        if (current.hasPermission("project:update")
                && !"PROJECT".equals(dataScopeService.effectiveScope(current, DataModule.PROJECT))) {
            return;
        }
        if (current.getEmployeeId() != null && current.getEmployeeId().equals(team.getManagerEmployeeId())) {
            return;
        }
        throw BusinessException.forbidden("仅项目负责人或授权管理员可以维护该项目组");
    }

    private ProjectTeam requireTeam(Long id) {
        ProjectTeam team = teamMapper.selectById(id);
        if (team == null) {
            throw BusinessException.notFound("项目组不存在");
        }
        return team;
    }

    private void assertCodeUnique(String code, Long excludeId) {
        QueryWrapper<ProjectTeam> qw = new QueryWrapper<ProjectTeam>().eq("project_code", code);
        if (excludeId != null) {
            qw.ne("id", excludeId);
        }
        if (teamMapper.selectCount(qw) > 0) {
            throw BusinessException.conflict("项目编码已存在: " + code);
        }
    }

    private void assertManagerValid(Long managerEmployeeId) {
        Employee manager = employeeMapper.selectById(managerEmployeeId);
        if (manager == null || EmployeeStatus.RESIGNED.name().equals(manager.getEmploymentStatus())) {
            throw BusinessException.badRequest("项目负责人不存在或已离职");
        }
    }

    /** 批量组装：负责人姓名与在职成员数一次查回，避免每行两次查询（列表页 N+1） */
    private List<ProjectVO> toVOs(List<ProjectTeam> teams) {
        if (teams.isEmpty()) {
            return List.of();
        }
        Set<Long> managerIds = teams.stream().map(ProjectTeam::getManagerEmployeeId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Employee> managers = managerIds.isEmpty() ? Map.of()
                : employeeMapper.selectBatchIds(managerIds).stream()
                        .collect(Collectors.toMap(Employee::getId, Function.identity()));
        List<Long> teamIds = teams.stream().map(ProjectTeam::getId).toList();
        Map<Long, Integer> counts = new LinkedHashMap<>();
        if (!teamIds.isEmpty()) {
            memberMapper.selectList(new QueryWrapper<ProjectTeamMember>()
                            .in("project_team_id", teamIds).isNull("left_at"))
                    .forEach(m -> counts.merge(m.getProjectTeamId(), 1, Integer::sum));
        }
        List<ProjectVO> list = new ArrayList<>(teams.size());
        for (ProjectTeam team : teams) {
            list.add(buildVO(team, managers.get(team.getManagerEmployeeId()),
                    counts.getOrDefault(team.getId(), 0)));
        }
        return list;
    }

    /** 批量组装成员：员工与部门各一次查回 */
    private List<ProjectMemberVO> toMemberVOs(List<ProjectTeamMember> members) {
        if (members.isEmpty()) {
            return List.of();
        }
        Set<Long> employeeIds = members.stream().map(ProjectTeamMember::getEmployeeId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Employee> employees = employeeIds.isEmpty() ? Map.of()
                : employeeMapper.selectBatchIds(employeeIds).stream()
                        .collect(Collectors.toMap(Employee::getId, Function.identity()));
        Set<Long> deptIds = employees.values().stream().map(Employee::getDepartmentId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Department> departments = deptIds.isEmpty() ? Map.of()
                : departmentMapper.selectBatchIds(deptIds).stream()
                        .collect(Collectors.toMap(Department::getId, Function.identity()));
        List<ProjectMemberVO> list = new ArrayList<>(members.size());
        for (ProjectTeamMember member : members) {
            Employee employee = employees.get(member.getEmployeeId());
            Department dept = employee == null || employee.getDepartmentId() == null
                    ? null : departments.get(employee.getDepartmentId());
            list.add(buildMemberVO(member, employee, dept));
        }
        return list;
    }

    private ProjectVO toVO(ProjectTeam team) {
        Employee manager = team.getManagerEmployeeId() == null ? null
                : employeeMapper.selectById(team.getManagerEmployeeId());
        int memberCount = Math.toIntExact(memberMapper.selectCount(new QueryWrapper<ProjectTeamMember>()
                .eq("project_team_id", team.getId()).isNull("left_at")));
        return buildVO(team, manager, memberCount);
    }

    private ProjectVO buildVO(ProjectTeam team, Employee manager, int memberCount) {
        ProjectVO vo = new ProjectVO();
        vo.setId(team.getId());
        vo.setProjectCode(team.getProjectCode());
        vo.setProjectName(team.getProjectName());
        vo.setManagerEmployeeId(team.getManagerEmployeeId());
        vo.setManagerEmployeeName(manager == null ? null : manager.getEmployeeName());
        vo.setDescription(team.getDescription());
        vo.setStatus(team.getStatus());
        vo.setMemberCount(memberCount);
        return vo;
    }

    private ProjectMemberVO toMemberVO(ProjectTeamMember member) {
        Employee employee = member.getEmployeeId() == null ? null
                : employeeMapper.selectById(member.getEmployeeId());
        Department dept = employee == null || employee.getDepartmentId() == null ? null
                : departmentMapper.selectById(employee.getDepartmentId());
        return buildMemberVO(member, employee, dept);
    }

    private ProjectMemberVO buildMemberVO(ProjectTeamMember member, Employee employee, Department dept) {
        ProjectMemberVO vo = new ProjectMemberVO();
        vo.setId(member.getId());
        vo.setProjectTeamId(member.getProjectTeamId());
        vo.setEmployeeId(member.getEmployeeId());
        if (employee != null) {
            vo.setEmployeeNo(employee.getEmployeeNo());
            vo.setEmployeeName(employee.getEmployeeName());
            vo.setDepartmentName(dept == null ? null : dept.getDepartmentName());
        }
        vo.setMemberRole(member.getMemberRole());
        vo.setJoinedAt(member.getJoinedAt());
        vo.setLeftAt(member.getLeftAt());
        return vo;
    }
}
