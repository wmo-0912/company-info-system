package com.example.company.department.service;

import com.example.company.common.api.PageResponse;
import com.example.company.department.dto.DepartmentRequest;
import com.example.company.department.vo.DepartmentVO;
import com.example.company.employee.vo.EmployeeListItemVO;

import java.util.List;

public interface DepartmentService {

    List<DepartmentVO> tree();

    PageResponse<DepartmentVO> page(int page, int pageSize, String keyword, String status);

    DepartmentVO detail(Long id);

    DepartmentVO create(DepartmentRequest request);

    DepartmentVO update(Long id, DepartmentRequest request);

    void changeStatus(Long id, String status);

    PageResponse<EmployeeListItemVO> employees(Long id, int page, int pageSize, String status);
}
