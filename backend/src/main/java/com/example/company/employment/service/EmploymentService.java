package com.example.company.employment.service;

import com.example.company.common.api.PageResponse;
import com.example.company.employment.dto.ChangeRequestCreateDTO;
import com.example.company.employment.dto.ChangeRequestProcessDTO;
import com.example.company.employment.vo.ChangeRequestVO;
import com.example.company.employment.vo.EmploymentRecordVO;

import java.util.List;

public interface EmploymentService {

    PageResponse<ChangeRequestVO> page(int page, int pageSize, String status, String changeType, Long employeeId);

    ChangeRequestVO detail(Long id);

    ChangeRequestVO create(ChangeRequestCreateDTO dto);

    ChangeRequestVO submit(Long id);

    ChangeRequestVO process(Long id, ChangeRequestProcessDTO dto);

    ChangeRequestVO cancel(Long id);

    List<EmploymentRecordVO> recordsOfEmployee(Long employeeId);

    PageResponse<ChangeRequestVO> requestsOfEmployee(Long employeeId, int page, int pageSize);
}
