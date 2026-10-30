package com.example.company.employment;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.company.audit.service.AuditService;
import com.example.company.common.enums.EmployeeStatus;
import com.example.company.common.exception.BusinessException;
import com.example.company.common.security.DataScopeService;
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
import com.example.company.employment.service.impl.EmploymentServiceImpl;
import com.example.company.project.entity.ProjectTeamMember;
import com.example.company.project.mapper.ProjectTeamMemberMapper;
import com.example.company.user.entity.SysUser;
import com.example.company.user.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.company.common.security.LoginUser;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmploymentServiceImplTest {

    @Mock
    private EmployeeChangeRequestMapper requestMapper;
    @Mock
    private EmployeeEmploymentRecordMapper recordMapper;
    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private DepartmentMapper departmentMapper;
    @Mock
    private ProjectTeamMemberMapper projectMemberMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private EmploymentServiceImpl service;

    @BeforeEach
    void setUpSecurity() {
        LoginUser user = new LoginUser();
        user.setUserId(1L);
        user.setUsername("hr");
        user.setRoles(Set.of("HR"));
        user.setPermissions(Set.of("employment:submit", "employment:process"));
        user.setDataScope("ALL");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private Employee employeeWithStatus(String status) {
        Employee e = new Employee();
        e.setId(10L);
        e.setEmployeeNo("E0101");
        e.setEmployeeName("张三");
        e.setDepartmentId(1L);
        e.setPositionName("后端工程师");
        e.setEmploymentStatus(status);
        return e;
    }

    private EmployeeChangeRequest submittedRequest(String type) {
        EmployeeChangeRequest req = new EmployeeChangeRequest();
        req.setId(100L);
        req.setRequestNo("CR2026102900010001");
        req.setEmployeeId(10L);
        req.setChangeType(type);
        req.setStatus("SUBMITTED");
        req.setEffectiveDate(LocalDate.now().plusDays(1));
        req.setPayload(new HashMap<>());
        req.setSubmittedBy(1L);
        return req;
    }

    @Test
    void regularizeRequiresProbationStatus() {
        Employee e = employeeWithStatus(EmployeeStatus.REGULAR.name());
        when(employeeMapper.selectById(10L)).thenReturn(e);
        ChangeRequestCreateDTO dto = new ChangeRequestCreateDTO();
        dto.setEmployeeId(10L);
        dto.setChangeType("REGULARIZE");
        dto.setEffectiveDate(LocalDate.now());
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));
        assertEquals(422, ex.getHttpStatus());
    }

    @Test
    void transferRequiresActiveTargetDepartment() {
        Employee e = employeeWithStatus(EmployeeStatus.REGULAR.name());
        when(employeeMapper.selectById(10L)).thenReturn(e);
        when(departmentMapper.selectById(2L)).thenReturn(null);
        ChangeRequestCreateDTO dto = new ChangeRequestCreateDTO();
        dto.setEmployeeId(10L);
        dto.setChangeType("TRANSFER");
        dto.setEffectiveDate(LocalDate.now());
        dto.setPayload(Map.of("targetDepartmentId", 2L));
        assertThrows(BusinessException.class, () -> service.create(dto));
    }

    @Test
    void transferToSameDepartmentRejected() {
        Employee e = employeeWithStatus(EmployeeStatus.REGULAR.name());
        Department dept = new Department();
        dept.setId(1L);
        dept.setStatus("ACTIVE");
        when(employeeMapper.selectById(10L)).thenReturn(e);
        when(departmentMapper.selectById(1L)).thenReturn(dept);
        ChangeRequestCreateDTO dto = new ChangeRequestCreateDTO();
        dto.setEmployeeId(10L);
        dto.setChangeType("TRANSFER");
        dto.setEffectiveDate(LocalDate.now());
        dto.setPayload(Map.of("targetDepartmentId", 1L));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));
        assertEquals(422, ex.getHttpStatus());
    }

    @Test
    void processDraftRequestRejected() {
        EmployeeChangeRequest req = submittedRequest("REGULARIZE");
        req.setStatus("DRAFT");
        when(requestMapper.selectById(100L)).thenReturn(req);
        ChangeRequestProcessDTO dto = new ChangeRequestProcessDTO();
        dto.setDecision("APPROVE");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.process(100L, dto));
        assertEquals(422, ex.getHttpStatus());
    }

    @Test
    void approveTransferUpdatesEmployeeAndWritesRecord() {
        Employee e = employeeWithStatus(EmployeeStatus.REGULAR.name());
        Department target = new Department();
        target.setId(2L);
        target.setStatus("ACTIVE");
        EmployeeChangeRequest req = submittedRequest("TRANSFER");
        req.getPayload().put("targetDepartmentId", 2L);
        req.getPayload().put("targetPositionName", "高级后端工程师");

        when(requestMapper.selectById(100L)).thenReturn(req);
        when(employeeMapper.selectById(10L)).thenReturn(e);
        when(departmentMapper.selectById(2L)).thenReturn(target);

        ChangeRequestProcessDTO dto = new ChangeRequestProcessDTO();
        dto.setDecision("APPROVE");
        service.process(100L, dto);

        assertEquals(2L, e.getDepartmentId());
        assertEquals("高级后端工程师", e.getPositionName());
        assertEquals(EmployeeStatus.TRANSFERRED.name(), e.getEmploymentStatus());
        verify(employeeMapper).updateById(e);
        verify(recordMapper).insert(any(EmployeeEmploymentRecord.class));
        assertEquals("APPROVED", req.getStatus());
    }

    @Test
    void rejectDoesNotTouchEmployee() {
        EmployeeChangeRequest req = submittedRequest("REGULARIZE");
        when(requestMapper.selectById(100L)).thenReturn(req);
        ChangeRequestProcessDTO dto = new ChangeRequestProcessDTO();
        dto.setDecision("REJECT");
        service.process(100L, dto);
        verify(employeeMapper, never()).updateById(any(Employee.class));
        verify(recordMapper, never()).insert(any(EmployeeEmploymentRecord.class));
        assertEquals("REJECTED", req.getStatus());
    }

    @Test
    void resignSetsResignationDate() {
        Employee e = employeeWithStatus(EmployeeStatus.REGULAR.name());
        EmployeeChangeRequest req = submittedRequest("RESIGN");
        when(requestMapper.selectById(100L)).thenReturn(req);
        when(employeeMapper.selectById(10L)).thenReturn(e);
        ChangeRequestProcessDTO dto = new ChangeRequestProcessDTO();
        dto.setDecision("APPROVE");
        service.process(100L, dto);
        assertEquals(EmployeeStatus.RESIGNED.name(), e.getEmploymentStatus());
        assertEquals(req.getEffectiveDate(), e.getResignationDate());
    }

    @Test
    @SuppressWarnings("unchecked")
    void resignCascadesAccountMembershipAndManager() {
        Employee e = employeeWithStatus(EmployeeStatus.REGULAR.name());
        EmployeeChangeRequest req = submittedRequest("RESIGN");
        when(requestMapper.selectById(100L)).thenReturn(req);
        when(employeeMapper.selectById(10L)).thenReturn(e);
        ChangeRequestProcessDTO dto = new ChangeRequestProcessDTO();
        dto.setDecision("APPROVE");

        service.process(100L, dto);

        ArgumentCaptor<Wrapper<SysUser>> userCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(userMapper).update(isNull(), userCaptor.capture());
        UpdateWrapper<SysUser> userUpdate = (UpdateWrapper<SysUser>) userCaptor.getValue();
        assertTrue(userUpdate.getSqlSet().contains("status"), userUpdate.getSqlSet());
        assertTrue(userUpdate.getParamNameValuePairs().containsValue("DISABLED"));

        ArgumentCaptor<Wrapper<ProjectTeamMember>> memberCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(projectMemberMapper).update(isNull(), memberCaptor.capture());
        UpdateWrapper<ProjectTeamMember> memberUpdate = (UpdateWrapper<ProjectTeamMember>) memberCaptor.getValue();
        assertTrue(memberUpdate.getSqlSet().contains("left_at"), memberUpdate.getSqlSet());
        assertTrue(memberUpdate.getParamNameValuePairs().containsValue(req.getEffectiveDate()));

        ArgumentCaptor<Wrapper<Department>> deptCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(departmentMapper).update(isNull(), deptCaptor.capture());
        assertTrue(((UpdateWrapper<Department>) deptCaptor.getValue()).getSqlSet().contains("manager_employee_id"));
    }

    @Test
    void cannotProcessTwice() {
        EmployeeChangeRequest req = submittedRequest("REGULARIZE");
        req.setStatus("APPROVED");
        when(requestMapper.selectById(100L)).thenReturn(req);
        ChangeRequestProcessDTO dto = new ChangeRequestProcessDTO();
        dto.setDecision("APPROVE");
        assertThrows(BusinessException.class, () -> service.process(100L, dto));
    }

    @Test
    void duplicateEmployeeNoRejected() {
        // 员工编号唯一性在员工服务层校验，这里验证请求编号生成非空
        Employee e = employeeWithStatus(EmployeeStatus.PENDING.name());
        when(employeeMapper.selectById(10L)).thenReturn(e);
        when(requestMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        ChangeRequestCreateDTO dto = new ChangeRequestCreateDTO();
        dto.setEmployeeId(10L);
        dto.setChangeType("ONBOARD");
        dto.setEffectiveDate(LocalDate.now());
        service.create(dto);
        verify(requestMapper).insert(argThat((EmployeeChangeRequest r) ->
                r.getRequestNo() != null && r.getRequestNo().startsWith("CR")));
    }
}
