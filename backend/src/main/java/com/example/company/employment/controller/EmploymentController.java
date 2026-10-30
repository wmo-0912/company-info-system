package com.example.company.employment.controller;

import com.example.company.common.api.ApiResponse;
import com.example.company.common.api.PageResponse;
import com.example.company.employment.dto.ChangeRequestCreateDTO;
import com.example.company.employment.dto.ChangeRequestProcessDTO;
import com.example.company.employment.service.EmploymentService;
import com.example.company.employment.vo.ChangeRequestVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "入转调离流程")
@RestController
@RequestMapping("/api/v1/employment/requests")
@RequiredArgsConstructor
public class EmploymentController {

    private final EmploymentService employmentService;

    @Operation(summary = "申请分页列表")
    @GetMapping
    @PreAuthorize("hasAuthority('employment:submit') or hasAuthority('employment:process') or hasAuthority('employee:read')")
    public ApiResponse<PageResponse<ChangeRequestVO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String changeType,
            @RequestParam(required = false) Long employeeId) {
        return ApiResponse.ok(employmentService.page(page, pageSize, status, changeType, employeeId));
    }

    @Operation(summary = "申请详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('employment:submit') or hasAuthority('employment:process') or hasAuthority('employee:read')")
    public ApiResponse<ChangeRequestVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(employmentService.detail(id));
    }

    @Operation(summary = "创建申请（草稿）")
    @PostMapping
    @PreAuthorize("hasAuthority('employment:submit')")
    public ApiResponse<ChangeRequestVO> create(@Valid @RequestBody ChangeRequestCreateDTO dto) {
        return ApiResponse.ok(employmentService.create(dto));
    }

    @Operation(summary = "提交申请")
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('employment:submit')")
    public ApiResponse<ChangeRequestVO> submit(@PathVariable Long id) {
        return ApiResponse.ok(employmentService.submit(id));
    }

    @Operation(summary = "办理（批准/驳回）申请")
    @PostMapping("/{id}/process")
    @PreAuthorize("hasAuthority('employment:process')")
    public ApiResponse<ChangeRequestVO> process(@PathVariable Long id,
                                                @Valid @RequestBody ChangeRequestProcessDTO dto) {
        return ApiResponse.ok(employmentService.process(id, dto));
    }

    @Operation(summary = "取消申请")
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('employment:submit')")
    public ApiResponse<ChangeRequestVO> cancel(@PathVariable Long id) {
        return ApiResponse.ok(employmentService.cancel(id));
    }
}
