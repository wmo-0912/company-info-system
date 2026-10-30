package com.example.company.project;

import com.example.company.audit.service.AuditService;
import com.example.company.common.exception.BusinessException;
import com.example.company.common.security.DataScopeService;
import com.example.company.common.security.LoginUser;
import com.example.company.department.mapper.DepartmentMapper;
import com.example.company.employee.entity.Employee;
import com.example.company.employee.mapper.EmployeeMapper;
import com.example.company.project.entity.ProjectTeam;
import com.example.company.project.entity.ProjectTeamMember;
import com.example.company.project.mapper.ProjectTeamMapper;
import com.example.company.project.mapper.ProjectTeamMemberMapper;
import com.example.company.project.service.impl.ProjectServiceImpl;
import com.example.company.project.vo.ProjectMemberVO;
import com.example.company.project.vo.ProjectVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 项目组按 ID 直查的越权防护（P0-3）：详情与成员列表必须落在数据范围内 */
@ExtendWith(MockitoExtension.class)
class ProjectAccessTest {

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
        user.setUsername("pm");
        user.setRoles(Set.of("PROJECT_MANAGER"));
        user.setPermissions(Set.of("project:read"));
        user.setDataScope("PROJECT");
        user.setEmployeeId(7L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private ProjectTeam team() {
        ProjectTeam t = new ProjectTeam();
        t.setId(100L);
        t.setProjectCode("P100");
        t.setProjectName("项目100");
        t.setManagerEmployeeId(7L);
        t.setStatus("ACTIVE");
        return t;
    }

    @Test
    void detailRejectedWhenOutsideDataScope() {
        when(teamMapper.selectById(100L)).thenReturn(team());
        when(dataScopeService.canAccessProject(any(), any(), any())).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.detail(100L));
        assertEquals(403, ex.getHttpStatus());
        verify(memberMapper, never()).selectCount(any());
    }

    @Test
    void membersRejectedWhenOutsideDataScope() {
        when(teamMapper.selectById(100L)).thenReturn(team());
        when(dataScopeService.canAccessProject(any(), any(), any())).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.members(100L));
        assertEquals(403, ex.getHttpStatus());
        verify(memberMapper, never()).selectList(any());
    }

    @Test
    void detailAllowedWhenInsideDataScope() {
        when(teamMapper.selectById(100L)).thenReturn(team());
        when(dataScopeService.canAccessProject(any(), any(), any())).thenReturn(true);
        Employee manager = new Employee();
        manager.setId(7L);
        manager.setEmployeeName("张经理");
        when(employeeMapper.selectById(7L)).thenReturn(manager);
        when(memberMapper.selectCount(any())).thenReturn(2L);

        ProjectVO vo = service.detail(100L);
        assertEquals("P100", vo.getProjectCode());
        assertEquals("张经理", vo.getManagerEmployeeName());
        assertEquals(2, vo.getMemberCount());
    }

    @Test
    void membersAllowedWhenInsideDataScope() {
        when(teamMapper.selectById(100L)).thenReturn(team());
        when(dataScopeService.canAccessProject(any(), any(), any())).thenReturn(true);
        ProjectTeamMember member = new ProjectTeamMember();
        member.setId(1L);
        member.setProjectTeamId(100L);
        member.setEmployeeId(11L);
        member.setMemberRole("DEV");
        member.setJoinedAt(LocalDate.now());
        when(memberMapper.selectList(any())).thenReturn(List.of(member));
        Employee employee = new Employee();
        employee.setId(11L);
        employee.setEmployeeNo("E11");
        employee.setEmployeeName("李工");
        when(employeeMapper.selectById(11L)).thenReturn(employee);

        List<ProjectMemberVO> members = service.members(100L);
        assertEquals(1, members.size());
        assertEquals("E11", members.get(0).getEmployeeNo());
        assertEquals("李工", members.get(0).getEmployeeName());
    }
}
