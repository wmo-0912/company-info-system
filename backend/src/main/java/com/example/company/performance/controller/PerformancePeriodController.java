package com.example.company.performance.controller;

import com.example.company.common.api.ApiResponse;
import com.example.company.performance.service.PerformancePeriodService;
import com.example.company.performance.vo.PerformancePeriodVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "绩效周期")
@RestController
@RequestMapping("/api/v1/performance-periods")
@RequiredArgsConstructor
public class PerformancePeriodController {

    private final PerformancePeriodService periodService;

    @Operation(summary = "已登记的绩效周期（含锁定状态）")
    @GetMapping
    @PreAuthorize("hasAuthority('performance:read')")
    public ApiResponse<List<PerformancePeriodVO>> list() {
        return ApiResponse.ok(periodService.list());
    }

    @Operation(summary = "锁定/解锁绩效周期（锁定后该周期不可新增、修改、提交评价）")
    @PatchMapping("/{periodKey}/lock")
    @PreAuthorize("hasAuthority('performance:review')")
    public ApiResponse<PerformancePeriodVO> setLocked(@PathVariable String periodKey,
                                                      @Valid @RequestBody LockBody body) {
        return ApiResponse.ok(periodService.setLocked(periodKey, body.getLocked()));
    }

    @Data
    public static class LockBody {
        @NotNull(message = "锁定状态不能为空")
        private Boolean locked;
    }
}
