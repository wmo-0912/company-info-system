package com.example.company.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.company.audit.service.AuditService;
import com.example.company.common.api.ApiResponse;
import com.example.company.common.exception.BusinessException;
import com.example.company.common.security.SecurityUtils;
import com.example.company.system.dto.ConfigUpdateRequest;
import com.example.company.system.entity.SysConfig;
import com.example.company.system.mapper.SysConfigMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@Tag(name = "系统配置")
@RestController
@RequestMapping("/api/v1/system/config")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SysConfigMapper configMapper;
    private final AuditService auditService;

    @Operation(summary = "全部配置")
    @GetMapping
    @PreAuthorize("hasAuthority('system:config:read')")
    public ApiResponse<List<SysConfig>> list() {
        return ApiResponse.ok(configMapper.selectList(new QueryWrapper<SysConfig>().orderByAsc("config_key")));
    }

    @Operation(summary = "新增或更新配置（不存储密钥类配置）")
    @PutMapping
    @PreAuthorize("hasAuthority('system:config:update')")
    @Transactional
    public ApiResponse<SysConfig> upsert(@Valid @RequestBody ConfigUpdateRequest request) {
        String key = request.getConfigKey().toLowerCase();
        if (key.contains("secret") || key.contains("password") || key.contains("token")) {
            throw BusinessException.badRequest("密钥类配置不允许存入系统配置，请使用环境变量");
        }
        SysConfig config = configMapper.selectOne(new QueryWrapper<SysConfig>()
                .eq("config_key", request.getConfigKey()));
        if (config == null) {
            config = new SysConfig();
            config.setConfigKey(request.getConfigKey());
            config.setConfigValue(request.getConfigValue());
            config.setDescription(request.getDescription());
            config.setUpdatedBy(SecurityUtils.getCurrentUserId());
            config.setUpdatedAt(OffsetDateTime.now());
            configMapper.insert(config);
        } else {
            config.setConfigValue(request.getConfigValue());
            config.setDescription(request.getDescription());
            config.setUpdatedBy(SecurityUtils.getCurrentUserId());
            config.setUpdatedAt(OffsetDateTime.now());
            configMapper.updateById(config);
        }
        auditService.success("system:config:update", "config", config.getConfigKey(), "更新系统配置");
        return ApiResponse.ok(config);
    }
}
