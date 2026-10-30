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
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 数据范围服务：所有需要数据权限的查询必须通过本服务计算可见员工集合。
 * 返回 null 表示不限制（ALL）；返回集合表示仅允许集合内的员工 ID。
 */
@Service
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
        String scope = user.getDataScope() == null ? DataScopeType.SELF.name() : user.getDataScope();
        return switch (DataScopeType.valueOf(scope)) {
            case ALL -> null;
            case SELF -> user.getEmployeeId() == null ? Set.of() : Set.of(user.getEmployeeId());
            case PROJECT -> projectVisibleEmployeeIds(user.getEmployeeId());
            case DEPARTMENT -> departmentVisibleEmployeeIds(user.getEmployeeId());
        };
    }

    private Set<Long> projectVisibleEmployeeIds(Long managerEmployeeId) {
        if (managerEmployeeId == null) {
            return Set.of();
        }
        List<ProjectTeam> teams = projectTeamMapper.selectList(new QueryWrapper<ProjectTeam>()
                .eq("manager_employee_id", managerEmployeeId));
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
}
