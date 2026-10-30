package com.example.company.department;

import com.example.company.audit.service.AuditService;
import com.example.company.common.exception.BusinessException;
import com.example.company.department.dto.DepartmentRequest;
import com.example.company.department.entity.Department;
import com.example.company.department.mapper.DepartmentMapper;
import com.example.company.department.service.impl.DepartmentServiceImpl;
import com.example.company.employee.mapper.EmployeeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceImplTest {

    @Mock
    private DepartmentMapper departmentMapper;
    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private DepartmentServiceImpl service;

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
}
