package com.example.company.department;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.company.audit.service.AuditService;
import com.example.company.common.api.PageResponse;
import com.example.company.common.exception.BusinessException;
import com.example.company.common.security.DataScopeService;
import com.example.company.common.security.LoginUser;
import com.example.company.department.dto.DepartmentRequest;
import com.example.company.department.entity.Department;
import com.example.company.department.mapper.DepartmentMapper;
import com.example.company.department.service.impl.DepartmentServiceImpl;
import com.example.company.employee.entity.Employee;
import com.example.company.employee.mapper.EmployeeMapper;
import com.example.company.employee.vo.EmployeeListItemVO;
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

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceImplTest {

    @Mock
    private DepartmentMapper departmentMapper;
    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private DepartmentServiceImpl service;

    @BeforeEach
    void setUpSecurity() {
        LoginUser user = new LoginUser();
        user.setUserId(1L);
        user.setUsername("hr");
        user.setRoles(Set.of("HR"));
        user.setPermissions(Set.of("department:read", "employee:read"));
        user.setDataScope("ALL");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private Department dept(Long id, Long parentId) {
        Department d = new Department();
        d.setId(id);
        d.setParentId(parentId);
        d.setDepartmentCode("D" + id);
        d.setDepartmentName("部门" + id);
        d.setStatus("ACTIVE");
        return d;
    }

    @Test
    void rejectSelfAsParent() {
        when(departmentMapper.selectById(1L)).thenReturn(dept(1L, null));
        DepartmentRequest req = new DepartmentRequest();
        req.setDepartmentCode("D1");
        req.setDepartmentName("部门1");
        req.setParentId(1L);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(1L, req));
        assertEquals(422, ex.getHttpStatus());
    }

    @Test
    void rejectCycle() {
        // 1 <- 2 <- 3；将 1 的父部门改为 3 形成环
        when(departmentMapper.selectById(1L)).thenReturn(dept(1L, null));
        when(departmentMapper.selectById(3L)).thenReturn(dept(3L, 2L));
        when(departmentMapper.selectById(2L)).thenReturn(dept(2L, 1L));
        DepartmentRequest req = new DepartmentRequest();
        req.setDepartmentCode("D1");
        req.setDepartmentName("部门1");
        req.setParentId(3L);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(1L, req));
        assertEquals(422, ex.getHttpStatus());
    }

    @Test
    void rejectDisableWithActiveChildren() {
        when(departmentMapper.selectById(1L)).thenReturn(dept(1L, null));
        when(departmentMapper.selectCount(org.mockito.ArgumentMatchers.any())).thenReturn(1L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.changeStatus(1L, "DISABLED"));
        assertEquals(409, ex.getHttpStatus());
    }

    @Test
    void rejectDuplicateCode() {
        when(departmentMapper.selectCount(org.mockito.ArgumentMatchers.any())).thenReturn(1L);
        DepartmentRequest req = new DepartmentRequest();
        req.setDepartmentCode("DUP");
        req.setDepartmentName("重复编码");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req));
        assertEquals(409, ex.getHttpStatus());
    }

    @Test
    void departmentEmployeesReturnEmptyWhenNoVisibleEmployee() {
        when(departmentMapper.selectById(1L)).thenReturn(dept(1L, null));
        when(dataScopeService.visibleEmployeeIds(any(), any())).thenReturn(Set.of());

        PageResponse<EmployeeListItemVO> page = service.employees(1L, 1, 10, null);
        assertEquals(0, page.getTotal());
        assertTrue(page.getRecords().isEmpty());
        verify(employeeMapper, never()).selectPage(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void departmentEmployeesFilteredByVisibleEmployeeIds() {
        when(departmentMapper.selectById(1L)).thenReturn(dept(1L, null));
        when(dataScopeService.visibleEmployeeIds(any(), any())).thenReturn(Set.of(9L, 10L));
        Page<Employee> dbPage = new Page<>(1, 10);
        dbPage.setRecords(List.of());
        dbPage.setTotal(0);
        when(employeeMapper.selectPage(any(), any())).thenReturn(dbPage);

        service.employees(1L, 1, 10, null);

        ArgumentCaptor<Wrapper<Employee>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(employeeMapper).selectPage(any(), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("department_id"), sql);
        assertTrue(sql.contains("id IN"), sql);
    }
}
