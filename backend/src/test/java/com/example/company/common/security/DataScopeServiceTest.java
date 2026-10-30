package com.example.company.common.security;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.company.department.entity.Department;
import com.example.company.department.mapper.DepartmentMapper;
import com.example.company.employee.mapper.EmployeeMapper;
import com.example.company.project.entity.ProjectTeam;
import com.example.company.project.entity.ProjectTeamMember;
import com.example.company.project.mapper.ProjectTeamMapper;
import com.example.company.project.mapper.ProjectTeamMemberMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataScopeServiceTest {

    @Mock
    private ProjectTeamMapper projectTeamMapper;
    @Mock
    private ProjectTeamMemberMapper projectTeamMemberMapper;
    @Mock
    private DepartmentMapper departmentMapper;
    @Mock
    private EmployeeMapper employeeMapper;

    @InjectMocks
    private DataScopeService dataScopeService;

    private LoginUser user(String scope, Long employeeId) {
        LoginUser user = new LoginUser();
        user.setUserId(1L);
        user.setUsername("u");
        user.setDataScope(scope);
        user.setEmployeeId(employeeId);
        return user;
    }

    @Test
    void allScopeReturnsNull() {
        assertNull(dataScopeService.visibleEmployeeIds(user("ALL", null)));
    }

    @Test
    void selfScopeReturnsOnlySelf() {
        assertEquals(Set.of(5L), dataScopeService.visibleEmployeeIds(user("SELF", 5L)));
        assertTrue(dataScopeService.visibleEmployeeIds(user("SELF", null)).isEmpty());
    }

    @Test
    void projectScopeContainsMembersOfManagedTeams() {
        ProjectTeam team = new ProjectTeam();
        team.setId(100L);
        team.setManagerEmployeeId(7L);
        when(projectTeamMapper.selectList(any())).thenReturn(List.of(team));
        ProjectTeamMember m1 = new ProjectTeamMember();
        m1.setEmployeeId(11L);
        ProjectTeamMember m2 = new ProjectTeamMember();
        m2.setEmployeeId(12L);
        when(projectTeamMemberMapper.selectList(any())).thenReturn(List.of(m1, m2));

        Set<Long> visible = dataScopeService.visibleEmployeeIds(user("PROJECT", 7L));
        assertEquals(Set.of(7L, 11L, 12L), visible);
    }

    @Test
    void departmentScopeExpandsChildren() {
        Department root = dept(1L, null);
        Department child = dept(2L, 1L);
        Department grandChild = dept(3L, 2L);
        Department other = dept(4L, null);
        when(departmentMapper.selectList(null)).thenReturn(List.of(root, child, grandChild, other));

        Set<Long> ids = dataScopeService.expandDepartmentIds(1L);
        assertEquals(Set.of(1L, 2L, 3L), ids);
    }

    private Department dept(Long id, Long parentId) {
        Department d = new Department();
        d.setId(id);
        d.setParentId(parentId);
        return d;
    }
}
