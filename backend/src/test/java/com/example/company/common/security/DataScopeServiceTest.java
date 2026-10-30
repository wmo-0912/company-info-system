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
    void unknownScopeFallsBackToSelfInsteadOfFailing() {
        // 脏数据/历史角色写入的非法枚举值不能变成 500，按最小权限 SELF 处理
        assertEquals(Set.of(5L), dataScopeService.visibleEmployeeIds(user("DEPARTMENT_X", 5L)));
        assertEquals(Set.of(5L), dataScopeService.visibleEmployeeIds(user(null, 5L)));
        assertNull(dataScopeService.visibleEmployeeIds(user(" all ", null)), "大小写与空格容错");
    }

    @Test
    void unknownScopeProjectAccessFallsBackToSelf() {
        ProjectTeam team = new ProjectTeam();
        team.setId(100L);
        team.setManagerEmployeeId(7L);
        LoginUser outsider = user("SELF", 9L);
        when(projectTeamMemberMapper.selectCount(any())).thenReturn(0L);

        assertFalse(dataScopeService.canAccessProject(outsider, team));
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
    void projectScopeExcludesClosedTeams() {
        when(projectTeamMapper.selectList(any())).thenReturn(List.of());

        Set<Long> visible = dataScopeService.visibleEmployeeIds(user("PROJECT", 7L));

        assertEquals(Set.of(7L), visible);
        org.mockito.ArgumentCaptor<QueryWrapper<ProjectTeam>> captor =
                org.mockito.ArgumentCaptor.forClass(QueryWrapper.class);
        org.mockito.Mockito.verify(projectTeamMapper).selectList(captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("status"),
                "数据范围查询应排除已关闭的项目组: " + captor.getValue().getSqlSegment());
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

    @Test
    void canAccessProjectAllowsAllScope() {
        assertTrue(dataScopeService.canAccessProject(user("ALL", 99L), projectTeam(7L)));
    }

    @Test
    void canAccessProjectAllowsManagerInProjectScope() {
        assertTrue(dataScopeService.canAccessProject(user("PROJECT", 7L), projectTeam(7L)));
    }

    @Test
    void canAccessProjectChecksActiveMembershipInProjectScope() {
        when(projectTeamMemberMapper.selectCount(any())).thenReturn(1L);
        assertTrue(dataScopeService.canAccessProject(user("PROJECT", 11L), projectTeam(7L)));
    }

    @Test
    void canAccessProjectRejectsOutsiderInProjectScope() {
        when(projectTeamMemberMapper.selectCount(any())).thenReturn(0L);
        assertFalse(dataScopeService.canAccessProject(user("PROJECT", 12L), projectTeam(7L)));
    }

    @Test
    void canAccessProjectRejectsSelfScopeWithoutMembership() {
        when(projectTeamMemberMapper.selectCount(any())).thenReturn(0L);
        assertFalse(dataScopeService.canAccessProject(user("SELF", 12L), projectTeam(7L)));
    }

    @Test
    void canAccessProjectRejectsNullTeam() {
        assertFalse(dataScopeService.canAccessProject(user("PROJECT", 7L), null));
    }

    @Test
    void canAccessProjectRejectsUserWithoutEmployee() {
        assertFalse(dataScopeService.canAccessProject(user("PROJECT", null), projectTeam(7L)));
    }

    private ProjectTeam projectTeam(Long managerEmployeeId) {
        ProjectTeam t = new ProjectTeam();
        t.setId(100L);
        t.setManagerEmployeeId(managerEmployeeId);
        return t;
    }

    private Department dept(Long id, Long parentId) {
        Department d = new Department();
        d.setId(id);
        d.setParentId(parentId);
        return d;
    }
}
