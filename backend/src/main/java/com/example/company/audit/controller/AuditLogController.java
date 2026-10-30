package com.example.company.audit.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.company.audit.entity.AuditLog;
import com.example.company.audit.mapper.AuditLogMapper;
import com.example.company.common.api.ApiResponse;
import com.example.company.common.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "审计日志")
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogMapper auditLogMapper;

    @Operation(summary = "审计日志分页查询")
    @GetMapping
    @PreAuthorize("hasAuthority('audit:read')")
    public ApiResponse<PageResponse<AuditLog>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String actorName,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        QueryWrapper<AuditLog> qw = new QueryWrapper<>();
        if (StringUtils.hasText(actorName)) {
            qw.like("actor_name", actorName);
        }
        if (StringUtils.hasText(resourceType)) {
            qw.eq("resource_type", resourceType);
        }
        if (startDate != null) {
            qw.ge("created_at", startDate.atStartOfDay());
        }
        if (endDate != null) {
            qw.lt("created_at", endDate.plusDays(1).atStartOfDay());
        }
        qw.orderByDesc("created_at");
        Page<AuditLog> result = auditLogMapper.selectPage(new Page<>(page, pageSize), qw);
        return ApiResponse.ok(PageResponse.from(result, a -> a));
    }
}
