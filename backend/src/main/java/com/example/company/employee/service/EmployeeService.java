package com.example.company.employee.service;

import com.example.company.common.api.PageResponse;
import com.example.company.employee.dto.EmployeeCreateRequest;
import com.example.company.employee.dto.EmployeeUpdateRequest;
import com.example.company.employee.vo.EmployeeDetailVO;
import com.example.company.employee.vo.EmployeeListItemVO;

public interface EmployeeService {

    PageResponse<EmployeeListItemVO> page(int page, int pageSize, String keyword, Long departmentId, String status);

    EmployeeDetailVO detail(Long id);

    EmployeeDetailVO create(EmployeeCreateRequest request);

    EmployeeDetailVO update(Long id, EmployeeUpdateRequest request);

    /** 归档（软删除）员工，仅限未走完离职流程不允许操作的场景外的管理操作 */
    void archive(Long id);
}
