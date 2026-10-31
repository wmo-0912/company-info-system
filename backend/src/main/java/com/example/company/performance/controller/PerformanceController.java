package com.example.company.performance.controller;

import com.example.company.common.api.ApiResponse;
import com.example.company.common.api.PageResponse;
import com.example.company.performance.dto.PerformanceReviewRequest;
import com.example.company.performance.dto.ReturnReviewRequest;
import com.example.company.performance.service.PerformanceService;
import com.example.company.performance.vo.PerformanceReviewVO;
import com.example.company.performance.vo.PerformanceSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "绩效考核")
@RestController
@RequestMapping("/api/v1/performance-reviews")
@RequiredArgsConstructor
public class PerformanceController {

    private final PerformanceService performanceService;

    @Operation(summary = "绩效分页列表（按数据范围过滤）")
    @GetMapping
    @PreAuthorize("hasAuthority('performance:read')")
    public ApiResponse<PageResponse<PerformanceReviewVO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String periodType,
            @RequestParam(required = false) String periodKey,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(performanceService.page(page, pageSize, periodType, periodKey, status));
    }

    @Operation(summary = "我的绩效（本人被评价记录）")
    @GetMapping("/my")
    @PreAuthorize("hasAuthority('performance:read')")
    public ApiResponse<PageResponse<PerformanceReviewVO>> my(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        return ApiResponse.ok(performanceService.my(page, pageSize));
    }

    @Operation(summary = "绩效汇总（按周期统计，含部门/项目组维度）")
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('performance:review')")
    public ApiResponse<PerformanceSummaryVO> summary(@RequestParam(required = false) String periodType,
                                                     @RequestParam(required = false) String periodKey) {
        return ApiResponse.ok(performanceService.summary(periodType, periodKey));
    }

    @Operation(summary = "绩效详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('performance:read')")
    public ApiResponse<PerformanceReviewVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(performanceService.detail(id));
    }

    @Operation(summary = "创建绩效评价（草稿）")
    @PostMapping
    @PreAuthorize("hasAuthority('performance:create')")
    public ApiResponse<PerformanceReviewVO> create(@Valid @RequestBody PerformanceReviewRequest request) {
        return ApiResponse.ok(performanceService.create(request));
    }

    @Operation(summary = "更新草稿评价")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('performance:update')")
    public ApiResponse<PerformanceReviewVO> update(@PathVariable Long id,
                                                   @Valid @RequestBody PerformanceReviewRequest request) {
        return ApiResponse.ok(performanceService.update(id, request));
    }

    @Operation(summary = "提交评价")
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('performance:submit')")
    public ApiResponse<PerformanceReviewVO> submit(@PathVariable Long id) {
        return ApiResponse.ok(performanceService.submit(id));
    }

    @Operation(summary = "退回评价（人事复核）")
    @PostMapping("/{id}/return")
    @PreAuthorize("hasAuthority('performance:review')")
    public ApiResponse<PerformanceReviewVO> returnForRevision(@PathVariable Long id,
                                                             @Valid @RequestBody(required = false)
                                                             ReturnReviewRequest request) {
        return ApiResponse.ok(performanceService.returnForRevision(id, request == null ? null : request.getReason()));
    }
}
