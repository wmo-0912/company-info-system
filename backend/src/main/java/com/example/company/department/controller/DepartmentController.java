package com.example.company.department.controller;

import com.example.company.common.api.ApiResponse;
import com.example.company.common.api.PageResponse;
import com.example.company.department.dto.DepartmentRequest;
import com.example.company.department.service.DepartmentService;
import com.example.company.department.vo.DepartmentVO;
import com.example.company.employee.vo.EmployeeListItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "部门管理")
@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @Operation(summary = "部门树")
    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('department:read')")
    public ApiResponse<List<DepartmentVO>> tree() {
        return ApiResponse.ok(departmentService.tree());
    }

    @Operation(summary = "部门分页列表")
    @GetMapping
    @PreAuthorize("hasAuthority('department:read')")
    public ApiResponse<PageResponse<DepartmentVO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(departmentService.page(page, pageSize, keyword, status));
    }

    @Operation(summary = "部门详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('department:read')")
    public ApiResponse<DepartmentVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(departmentService.detail(id));
    }

    @Operation(summary = "创建部门")
    @PostMapping
    @PreAuthorize("hasAuthority('department:create')")
    public ApiResponse<DepartmentVO> create(@Valid @RequestBody DepartmentRequest request) {
        return ApiResponse.ok(departmentService.create(request));
    }

    @Operation(summary = "更新部门")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('department:update')")
    public ApiResponse<DepartmentVO> update(@PathVariable Long id, @Valid @RequestBody DepartmentRequest request) {
        return ApiResponse.ok(departmentService.update(id, request));
    }

    @Operation(summary = "启用/停用部门")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('department:disable')")
    public ApiResponse<Void> changeStatus(@PathVariable Long id,
                                          @Valid @RequestBody StatusRequest request) {
        departmentService.changeStatus(id, request.getStatus());
        return ApiResponse.ok();
    }

    @Operation(summary = "部门员工列表")
    @GetMapping("/{id}/employees")
    @PreAuthorize("hasAuthority('department:read')")
    public ApiResponse<PageResponse<EmployeeListItemVO>> employees(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(departmentService.employees(id, page, pageSize, status));
    }

    @Data
    public static class StatusRequest {
        @NotBlank(message = "状态不能为空")
        private String status;
    }
}
