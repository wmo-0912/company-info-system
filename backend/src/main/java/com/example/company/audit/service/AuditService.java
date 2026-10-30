package com.example.company.audit.service;

import com.example.company.audit.entity.AuditLog;
import com.example.company.audit.mapper.AuditLogMapper;
import com.example.company.common.security.LoginUser;
import com.example.company.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 审计日志服务。记录操作者、动作、资源与结果；禁止记录密码、JWT、证件号等敏感值。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogMapper auditLogMapper;

    public void record(String action, String resourceType, Object resourceId, String result, String summary) {
        try {
            AuditLog logEntry = new AuditLog();
            LoginUser user = SecurityUtils.getCurrentUserOrNull();
            if (user != null) {
                logEntry.setActorUserId(user.getUserId());
                logEntry.setActorName(user.getUsername());
            }
            logEntry.setAction(action);
            logEntry.setResourceType(resourceType);
            logEntry.setResourceId(resourceId == null ? null : String.valueOf(resourceId));
            logEntry.setResult(result);
            logEntry.setSummary(summary);
            auditLogMapper.insert(logEntry);
        } catch (Exception e) {
            // 审计失败不阻断主流程，但记录到应用日志
            log.warn("写入审计日志失败: {}", e.getMessage());
        }
    }

    public void success(String action, String resourceType, Object resourceId, String summary) {
        record(action, resourceType, resourceId, "SUCCESS", summary);
    }

    public void failure(String action, String resourceType, Object resourceId, String summary) {
        record(action, resourceType, resourceId, "FAILURE", summary);
    }
}
