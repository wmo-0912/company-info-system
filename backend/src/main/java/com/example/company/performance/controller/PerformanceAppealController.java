package com.example.company.performance.controller;

import com.example.company.common.api.ApiResponse;
import com.example.company.common.api.PageResponse;
import com.example.company.performance.dto.AppealCreateRequest;
import com.example.company.performance.dto.AppealHandleRequest;
import com.example.company.performance.service.PerformanceAppealService;
import com.example.company.performance.vo.PerformanceAppealVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "绩效申诉")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PerformanceAppealController {

    private final PerformanceAppealService appealService;

    @Operation(summary = "对绩效评价提出申诉（仅本人收到的已提交评价）")
    @PostMapping("/performance-reviews/{id}/appeal")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PerformanceAppealVO> create(@PathVariable Long id,
                                                   @Valid @RequestBody AppealCreateRequest request) {
        return ApiResponse.ok(appealService.create(id, request.getReason()));
    }

    @Operation(summary = "申诉分页（人事/管理员）")
    @GetMapping("/performance-appeals")
    @PreAuthorize("hasAuthority('performance:review')")
    public ApiResponse<PageResponse<PerformanceAppealVO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(appealService.page(page, pageSize, status));
    }

    @Operation(summary = "我提出的申诉")
    @GetMapping("/performance-appeals/my")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PageResponse<PerformanceAppealVO>> my(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        return ApiResponse.ok(appealService.my(page, pageSize));
    }

    @Operation(summary = "处理申诉（受理后评价退回评价人复核）")
    @PostMapping("/performance-appeals/{id}/handle")
    @PreAuthorize("hasAuthority('performance:review')")
    public ApiResponse<PerformanceAppealVO> handle(@PathVariable Long id,
                                                   @Valid @RequestBody AppealHandleRequest request) {
        return ApiResponse.ok(appealService.handle(id, request.getDecision(), request.getComment()));
    }
}
