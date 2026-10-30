package com.example.company.employee.controller;

import com.example.company.common.api.ApiResponse;
import com.example.company.common.api.PageResponse;
import com.example.company.employee.dto.EmployeeCreateRequest;
import com.example.company.employee.dto.EmployeeUpdateRequest;
import com.example.company.employee.service.EmployeeService;
import com.example.company.employee.vo.EmployeeDetailVO;
import com.example.company.employee.vo.EmployeeListItemVO;
import com.example.company.employment.service.EmploymentService;
import com.example.company.employment.vo.ChangeRequestVO;
import com.example.company.employment.vo.EmploymentRecordVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "员工管理")
@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;
    private final EmploymentService employmentService;

    @Operation(summary = "员工分页列表")
    @GetMapping
    @PreAuthorize("hasAuthority('employee:read')")
    public ApiResponse<PageResponse<EmployeeListItemVO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(employeeService.page(page, pageSize, keyword, departmentId, status));
    }

    @Operation(summary = "员工详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('employee:read')")
    public ApiResponse<EmployeeDetailVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(employeeService.detail(id));
    }

    @Operation(summary = "创建员工")
    @PostMapping
    @PreAuthorize("hasAuthority('employee:create')")
    public ApiResponse<EmployeeDetailVO> create(@Valid @RequestBody EmployeeCreateRequest request) {
        return ApiResponse.ok(employeeService.create(request));
    }

    @Operation(summary = "编辑员工档案（不含流程控制字段）")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('employee:update')")
    public ApiResponse<EmployeeDetailVO> update(@PathVariable Long id,
                                                @Valid @RequestBody EmployeeUpdateRequest request) {
        return ApiResponse.ok(employeeService.update(id, request));
    }

    @Operation(summary = "归档员工（仅已离职）")
    @PatchMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('employee:archive')")
    public ApiResponse<Void> archive(@PathVariable Long id) {
        employeeService.archive(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "员工任职历史")
    @GetMapping("/{id}/employment-records")
    @PreAuthorize("hasAuthority('employee:read')")
    public ApiResponse<List<EmploymentRecordVO>> records(@PathVariable Long id) {
        return ApiResponse.ok(employmentService.recordsOfEmployee(id));
    }

    @Operation(summary = "员工入转调离记录")
    @GetMapping("/{id}/change-requests")
    @PreAuthorize("hasAuthority('employee:read')")
    public ApiResponse<PageResponse<ChangeRequestVO>> changeRequests(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        return ApiResponse.ok(employmentService.requestsOfEmployee(id, page, pageSize));
    }
}
