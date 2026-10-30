package com.example.company.common.security;

import com.example.company.common.enums.DataScopeType;
import com.example.company.employee.mapper.EmployeeMapper;
import com.example.company.project.entity.ProjectTeamMember;
import com.example.company.project.mapper.ProjectTeamMapper;
import com.example.company.project.mapper.ProjectTeamMemberMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.company.department.entity.Department;
import com.example.company.department.mapper.DepartmentMapper;
import com.example.company.project.entity.ProjectTeam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 数据范围服务：所有需要数据权限的查询必须通过本服务计算可见员工集合。
 * 返回 null 表示不限制（ALL）；返回集合表示仅允许集合内的员工 ID。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DataScopeService {

    private final ProjectTeamMapper projectTeamMapper;
    private final ProjectTeamMemberMapper projectTeamMemberMapper;
    private final DepartmentMapper departmentMapper;
    private final EmployeeMapper employeeMapper;

    /**
     * 计算当前用户可见的员工 ID 集合。
     *
     * @return null 表示可见全部；空集合表示不可见任何员工
     */
    public Set<Long> visibleEmployeeIds(LoginUser user) {
        return switch (parseScope(user.getDataScope())) {
            case ALL -> null;
            case SELF -> user.getEmployeeId() == null ? Set.of() : Set.of(user.getEmployeeId());
            case PROJECT -> projectVisibleEmployeeIds(user.getEmployeeId());
            case DEPARTMENT -> departmentVisibleEmployeeIds(user.getEmployeeId());
        };
    }

    /**
     * 解析数据范围枚举。脏数据/历史角色写入的非法值不能变成 500，按最小权限 SELF 处理并留日志，
     * 由管理员在角色管理里修正取值。
     */
    private DataScopeType parseScope(String raw) {
        if (raw == null || raw.isBlank()) {
            return DataScopeType.SELF;
        }
        try {
            return DataScopeType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("角色数据范围取值非法，按 SELF（仅本人）处理: {}", raw);
            return DataScopeType.SELF;
        }
    }

    private Set<Long> projectVisibleEmployeeIds(Long managerEmployeeId) {
        if (managerEmployeeId == null) {
            return Set.of();
        }
        // 已关闭的项目组不再扩大数据范围，避免历史成员长期可见
        List<ProjectTeam> teams = projectTeamMapper.selectList(new QueryWrapper<ProjectTeam>()
                .eq("manager_employee_id", managerEmployeeId)
                .eq("status", "ACTIVE"));
        Set<Long> result = new HashSet<>();
        result.add(managerEmployeeId); // 自己始终可见
        if (teams.isEmpty()) {
            return result;
        }
        List<Long> teamIds = teams.stream().map(ProjectTeam::getId).toList();
        List<ProjectTeamMember> members = projectTeamMemberMapper.selectList(new QueryWrapper<ProjectTeamMember>()
                .in("project_team_id", teamIds)
                .isNull("left_at"));
        members.forEach(m -> result.add(m.getEmployeeId()));
        return result;
    }

    private Set<Long> departmentVisibleEmployeeIds(Long employeeId) {
        if (employeeId == null) {
            return Set.of();
        }
        var self = employeeMapper.selectById(employeeId);
        if (self == null) {
            return Set.of();
        }
        Set<Long> deptIds = expandDepartmentIds(self.getDepartmentId());
        Set<Long> result = new HashSet<>();
        employeeMapper.selectList(new QueryWrapper<com.example.company.employee.entity.Employee>()
                        .in("department_id", deptIds))
                .forEach(e -> result.add(e.getId()));
        return result;
    }

    /** 返回部门及其所有后代部门 ID */
    public Set<Long> expandDepartmentIds(Long rootDepartmentId) {
        List<Department> all = departmentMapper.selectList(null);
        Set<Long> result = new HashSet<>();
        result.add(rootDepartmentId);
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(rootDepartmentId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            for (Department d : all) {
                if (current.equals(d.getParentId()) && result.add(d.getId())) {
                    queue.add(d.getId());
                }
            }
        }
        return result;
    }

    /** 校验目标员工是否在当前用户数据范围内 */
    public boolean canAccessEmployee(LoginUser user, Long employeeId) {
        Set<Long> visible = visibleEmployeeIds(user);
        return visible == null || visible.contains(employeeId);
    }

    /**
     * 校验项目组是否在当前用户数据范围内（用于详情、成员等按 ID 直查的读操作）。
     * ALL / DEPARTMENT 可见全部项目组（与列表口径一致）；
     * PROJECT 仅本人负责或本人在组的项目组；SELF 仅本人在组的项目组。
     */
    public boolean canAccessProject(LoginUser user, ProjectTeam team) {
        if (team == null) {
            return false;
        }
        return switch (parseScope(user.getDataScope())) {
            case ALL, DEPARTMENT -> true;
            case PROJECT -> isProjectManager(user, team) || isActiveMember(user.getEmployeeId(), team.getId());
            case SELF -> isActiveMember(user.getEmployeeId(), team.getId());
        };
    }

    private boolean isProjectManager(LoginUser user, ProjectTeam team) {
        return user.getEmployeeId() != null && user.getEmployeeId().equals(team.getManagerEmployeeId());
    }

    private boolean isActiveMember(Long employeeId, Long projectTeamId) {
        if (employeeId == null || projectTeamId == null) {
            return false;
        }
        return projectTeamMemberMapper.selectCount(new QueryWrapper<ProjectTeamMember>()
                .eq("project_team_id", projectTeamId)
                .eq("employee_id", employeeId)
                .isNull("left_at")) > 0;
    }
}
