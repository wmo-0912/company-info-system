package com.example.company.employee;

import com.example.company.audit.service.AuditService;
import com.example.company.common.enums.EmployeeStatus;
import com.example.company.common.exception.BusinessException;
import com.example.company.common.security.DataScopeService;
import com.example.company.common.security.LoginUser;
import com.example.company.department.entity.Department;
import com.example.company.department.mapper.DepartmentMapper;
import com.example.company.employee.dto.EmployeeCreateRequest;
import com.example.company.employee.dto.EmployeeUpdateRequest;
import com.example.company.employee.entity.Employee;
import com.example.company.employee.mapper.EmployeeMapper;
import com.example.company.employee.service.impl.EmployeeServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceImplTest {

    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private DepartmentMapper departmentMapper;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private EmployeeServiceImpl service;

    @BeforeEach
    void setUpSecurity() {
        LoginUser user = new LoginUser();
        user.setUserId(1L);
        user.setUsername("hr");
        user.setRoles(Set.of("HR"));
        user.setPermissions(Set.of("employee:read", "employee:create", "employee:update"));
        user.setDataScope("ALL");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private Department activeDept() {
        Department d = new Department();
        d.setId(1L);
        d.setDepartmentName("研发部");
        d.setStatus("ACTIVE");
        return d;
    }

    @Test
    void createRejectsDuplicateEmployeeNo() {
        when(employeeMapper.selectCount(any())).thenReturn(1L);
        EmployeeCreateRequest req = new EmployeeCreateRequest();
        req.setEmployeeNo("E0101");
        req.setEmployeeName("张三");
        req.setDepartmentId(1L);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req));
        assertEquals(409, ex.getHttpStatus());
    }

    @Test
    void updateRejectsResignedEmployee() {
        Employee e = new Employee();
        e.setId(10L);
        e.setEmploymentStatus(EmployeeStatus.RESIGNED.name());
        when(employeeMapper.selectById(10L)).thenReturn(e);
        EmployeeUpdateRequest req = new EmployeeUpdateRequest();
        req.setEmployeeName("张三");
        req.setDepartmentId(1L);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(10L, req));
        assertEquals(422, ex.getHttpStatus());
    }

    @Test
    void createRejectsInactiveDepartment() {
        Department dept = new Department();
        dept.setId(1L);
        dept.setStatus("DISABLED");
        when(employeeMapper.selectCount(any())).thenReturn(0L);
        when(departmentMapper.selectById(1L)).thenReturn(dept);
        EmployeeCreateRequest req = new EmployeeCreateRequest();
        req.setEmployeeNo("E0999");
        req.setEmployeeName("新员工");
        req.setDepartmentId(1L);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req));
        assertEquals(400, ex.getHttpStatus());
    }

    @Test
    void updateRejectsSelfAsManager() {
        Employee e = new Employee();
        e.setId(10L);
        e.setDepartmentId(1L);
        e.setEmploymentStatus(EmployeeStatus.REGULAR.name());
        when(employeeMapper.selectById(10L)).thenReturn(e);
        when(departmentMapper.selectById(1L)).thenReturn(activeDept());
        EmployeeUpdateRequest req = new EmployeeUpdateRequest();
        req.setEmployeeName("张三");
        req.setDepartmentId(1L);
        req.setManagerEmployeeId(10L);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(10L, req));
        assertEquals(400, ex.getHttpStatus());
    }

    private Employee employee(String status, Long departmentId, String positionName) {
        Employee e = new Employee();
        e.setId(10L);
        e.setDepartmentId(departmentId);
        e.setPositionName(positionName);
        e.setEmploymentStatus(status);
        return e;
    }

    private EmployeeUpdateRequest baseUpdateRequest(Long departmentId) {
        EmployeeUpdateRequest req = new EmployeeUpdateRequest();
        req.setEmployeeName("张三");
        req.setDepartmentId(departmentId);
        return req;
    }

    @Test
    void updateRejectsDepartmentChangeForOnboardedEmployee() {
        when(employeeMapper.selectById(10L)).thenReturn(employee(EmployeeStatus.REGULAR.name(), 1L, "后端工程师"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(10L, baseUpdateRequest(2L)));
        assertEquals(422, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("调岗"), ex.getMessage());
    }

    @Test
    void updateRejectsPositionChangeForOnboardedEmployee() {
        when(employeeMapper.selectById(10L)).thenReturn(employee(EmployeeStatus.REGULAR.name(), 1L, "后端工程师"));
        when(departmentMapper.selectById(1L)).thenReturn(activeDept());
        EmployeeUpdateRequest req = baseUpdateRequest(1L);
        req.setPositionName("前端工程师");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(10L, req));
        assertEquals(422, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("调岗"), ex.getMessage());
    }

    @Test
    void updateAllowsDepartmentFixBeforeOnboarding() {
        Employee e = employee(EmployeeStatus.PENDING.name(), 1L, null);
        when(employeeMapper.selectById(10L)).thenReturn(e);
        Department target = activeDept();
        target.setId(2L);
        when(departmentMapper.selectById(2L)).thenReturn(target);

        service.update(10L, baseUpdateRequest(2L));

        assertEquals(2L, e.getDepartmentId());
    }

    @Test
    void archiveRejectsNonResignedEmployee() {
        Employee e = new Employee();
        e.setId(10L);
        e.setEmploymentStatus(EmployeeStatus.REGULAR.name());
        when(employeeMapper.selectById(10L)).thenReturn(e);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.archive(10L));
        assertEquals(422, ex.getHttpStatus());
    }
}
