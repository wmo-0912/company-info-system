package com.example.company.project;

import com.example.company.audit.service.AuditService;
import com.example.company.common.exception.BusinessException;
import com.example.company.common.security.DataScopeService;
import com.example.company.common.security.LoginUser;
import com.example.company.department.mapper.DepartmentMapper;
import com.example.company.employee.entity.Employee;
import com.example.company.employee.mapper.EmployeeMapper;
import com.example.company.project.dto.ProjectMemberRequest;
import com.example.company.project.entity.ProjectTeam;
import com.example.company.project.entity.ProjectTeamMember;
import com.example.company.project.mapper.ProjectTeamMapper;
import com.example.company.project.mapper.ProjectTeamMemberMapper;
import com.example.company.project.service.impl.ProjectServiceImpl;
import com.example.company.project.vo.ProjectMemberVO;
import com.example.company.project.vo.ProjectVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 项目组生命周期：关闭 / 重新开启，以及成员加入、离开日期的可指定（P1-14/P1-15） */
@ExtendWith(MockitoExtension.class)
class ProjectLifecycleTest {

    @Mock
    private ProjectTeamMapper teamMapper;
    @Mock
    private ProjectTeamMemberMapper memberMapper;
    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private DepartmentMapper departmentMapper;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private ProjectServiceImpl service;

    @BeforeEach
    void setUpSecurity() {
        LoginUser user = new LoginUser();
        user.setUserId(1L);
        user.setUsername("admin");
        user.setRoles(Set.of("ADMIN"));
        user.setPermissions(Set.of("project:read", "project:update", "project:member:manage"));
        user.setDataScope("ALL");
        user.setEmployeeId(7L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private ProjectTeam team(String status) {
        ProjectTeam t = new ProjectTeam();
        t.setId(100L);
        t.setProjectCode("P100");
        t.setProjectName("项目100");
        t.setManagerEmployeeId(7L);
        t.setStatus(status);
        return t;
    }

    private void stubToVO(ProjectTeam team) {
        Employee manager = new Employee();
        manager.setId(team.getManagerEmployeeId());
        manager.setEmployeeName("张经理");
        when(employeeMapper.selectById(team.getManagerEmployeeId())).thenReturn(manager);
        when(memberMapper.selectCount(any())).thenReturn(0L);
    }

    @Test
    @DisplayName("关闭项目组：状态置 CLOSED 并记审计")
    void closeProjectSetsClosedStatusAndAudits() {
        ProjectTeam team = team("ACTIVE");
        when(teamMapper.selectById(100L)).thenReturn(team);
        stubToVO(team);

        ProjectVO vo = service.updateStatus(100L, "CLOSED");

        assertEquals("CLOSED", vo.getStatus());
        verify(teamMapper).updateById(any(ProjectTeam.class));
        ArgumentCaptor<String> summary = ArgumentCaptor.forClass(String.class);
        verify(auditService).success(eq("project:status"), eq("project"), eq(100L), summary.capture());
        assertTrue(summary.getValue().contains("关闭项目组"));
        assertTrue(summary.getValue().contains("项目100"));
    }

    @Test
    @DisplayName("重新开启项目组：状态回到 ACTIVE")
    void reopenProjectSetsActiveStatus() {
        ProjectTeam team = team("CLOSED");
        when(teamMapper.selectById(100L)).thenReturn(team);
        stubToVO(team);

        service.updateStatus(100L, "ACTIVE");

        assertEquals("ACTIVE", team.getStatus());
        verify(teamMapper).updateById(any(ProjectTeam.class));
    }

    @Test
    @DisplayName("状态重复时不写库、不记审计")
    void updateStatusIsIdempotent() {
        ProjectTeam team = team("ACTIVE");
        when(teamMapper.selectById(100L)).thenReturn(team);
        stubToVO(team);

        service.updateStatus(100L, "ACTIVE");

        verify(teamMapper, never()).updateById(any(ProjectTeam.class));
        verify(auditService, never()).success(any(), any(), any(), any());
    }

    @Test
    @DisplayName("非法状态值直接 400，不写库")
    void updateStatusRejectsUnknownValue() {
        when(teamMapper.selectById(100L)).thenReturn(team("ACTIVE"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateStatus(100L, "ARCHIVED"));

        assertEquals(400, ex.getHttpStatus());
        verify(teamMapper, never()).updateById(any(ProjectTeam.class));
    }

    @Test
    @DisplayName("新增成员可指定加入日期，用于历史项目补录")
    void addMemberUsesProvidedJoinDate() {
        ProjectTeam team = team("ACTIVE");
        when(teamMapper.selectById(100L)).thenReturn(team);
        Employee employee = new Employee();
        employee.setId(11L);
        employee.setEmployeeNo("E11");
        employee.setEmployeeName("李工");
        employee.setEmploymentStatus("REGULAR");
        when(employeeMapper.selectById(11L)).thenReturn(employee);

        ProjectMemberRequest request = new ProjectMemberRequest();
        request.setEmployeeId(11L);
        request.setMemberRole("DEV");
        request.setJoinedAt(LocalDate.of(2024, 1, 2));

        ProjectMemberVO vo = service.addMember(100L, request);

        ArgumentCaptor<ProjectTeamMember> captor = ArgumentCaptor.forClass(ProjectTeamMember.class);
        verify(memberMapper).insert(captor.capture());
        assertEquals(LocalDate.of(2024, 1, 2), captor.getValue().getJoinedAt());
        assertEquals(LocalDate.of(2024, 1, 2), vo.getJoinedAt());
    }

    @Test
    @DisplayName("新增成员不填加入日期时取当天")
    void addMemberDefaultsToToday() {
        ProjectTeam team = team("ACTIVE");
        when(teamMapper.selectById(100L)).thenReturn(team);
        Employee employee = new Employee();
        employee.setId(11L);
        employee.setEmploymentStatus("REGULAR");
        when(employeeMapper.selectById(11L)).thenReturn(employee);

        ProjectMemberRequest request = new ProjectMemberRequest();
        request.setEmployeeId(11L);
        service.addMember(100L, request);

        ArgumentCaptor<ProjectTeamMember> captor = ArgumentCaptor.forClass(ProjectTeamMember.class);
        verify(memberMapper).insert(captor.capture());
        assertEquals(LocalDate.now(), captor.getValue().getJoinedAt());
    }

    @Test
    @DisplayName("移出成员可指定离开日期，写入 left_at")
    void removeMemberUsesProvidedLeaveDate() {
        when(teamMapper.selectById(100L)).thenReturn(team("ACTIVE"));
        ProjectTeamMember member = new ProjectTeamMember();
        member.setId(1L);
        member.setProjectTeamId(100L);
        member.setEmployeeId(11L);
        member.setJoinedAt(LocalDate.of(2024, 1, 1));
        when(memberMapper.selectOne(any())).thenReturn(member);

        service.removeMember(100L, 11L, LocalDate.of(2024, 6, 30));

        assertEquals(LocalDate.of(2024, 6, 30), member.getLeftAt());
        verify(memberMapper).updateById(any(ProjectTeamMember.class));
    }

    @Test
    @DisplayName("离开日期早于加入日期直接 400")
    void removeMemberRejectsLeaveDateBeforeJoinDate() {
        when(teamMapper.selectById(100L)).thenReturn(team("ACTIVE"));
        ProjectTeamMember member = new ProjectTeamMember();
        member.setEmployeeId(11L);
        member.setJoinedAt(LocalDate.of(2024, 5, 1));
        when(memberMapper.selectOne(any())).thenReturn(member);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.removeMember(100L, 11L, LocalDate.of(2024, 4, 30)));

        assertEquals(400, ex.getHttpStatus());
        assertNull(member.getLeftAt());
        verify(memberMapper, never()).updateById(any(ProjectTeamMember.class));
    }

    @Test
    @DisplayName("关闭的项目组不能新增成员")
    void closedProjectRejectsNewMember() {
        when(teamMapper.selectById(100L)).thenReturn(team("CLOSED"));
        ProjectMemberRequest request = new ProjectMemberRequest();
        request.setEmployeeId(11L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.addMember(100L, request));

        assertEquals(422, ex.getHttpStatus());
        verify(memberMapper, never()).insert(any(ProjectTeamMember.class));
    }
}
