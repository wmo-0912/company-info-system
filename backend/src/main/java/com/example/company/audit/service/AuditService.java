package com.example.company.audit.service;

import com.example.company.audit.entity.AuditLog;
import com.example.company.audit.mapper.AuditLogMapper;
import com.example.company.common.security.LoginUser;
import com.example.company.common.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 审计日志服务。记录操作者、动作、资源、结果与来源 IP；禁止记录密码、JWT、证件号等敏感值。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    /** 与 sys_audit_log.ip_address 的列宽保持一致 */
    private static final int MAX_IP_LENGTH = 64;

    private final AuditLogMapper auditLogMapper;

    /** 记录当前登录用户的操作（从 SecurityContext 推导操作者） */
    public void record(String action, String resourceType, Object resourceId, String result, String summary) {
        LoginUser user = SecurityUtils.getCurrentUserOrNull();
        record(user == null ? null : user.getUserId(),
                user == null ? null : user.getUsername(),
                action, resourceType, resourceId, result, summary);
    }

    /**
     * 记录指定操作者的操作。登录失败等尚未建立 SecurityContext 的场景必须使用本方法，
     * 否则审计会丢失操作者信息。
     */
    public void record(Long actorUserId, String actorName, String action, String resourceType,
                       Object resourceId, String result, String summary) {
        try {
            AuditLog logEntry = new AuditLog();
            logEntry.setActorUserId(actorUserId);
            logEntry.setActorName(actorName);
            logEntry.setAction(action);
            logEntry.setResourceType(resourceType);
            logEntry.setResourceId(resourceId == null ? null : String.valueOf(resourceId));
            logEntry.setResult(result);
            logEntry.setIpAddress(currentIp());
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

    /** 指定操作者的成功/失败事件（用于登录等无 SecurityContext 的场景） */
    public void recordForActor(Long actorUserId, String actorName, String action, String resourceType,
                               Object resourceId, boolean success, String summary) {
        record(actorUserId, actorName, action, resourceType, resourceId,
                success ? "SUCCESS" : "FAILURE", summary);
    }

    /** 取真实客户端 IP：优先 X-Forwarded-For 第一个地址（Nginx 反代场景），其次 X-Real-IP、remoteAddr */
    private String currentIp() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return null;
        }
        HttpServletRequest request = servletAttributes.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = null;
        if (StringUtils.hasText(forwarded)) {
            ip = forwarded.split(",")[0].trim();
        }
        if (!StringUtils.hasText(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (!StringUtils.hasText(ip)) {
            ip = request.getRemoteAddr();
        }
        if (!StringUtils.hasText(ip)) {
            return null;
        }
        return ip.length() > MAX_IP_LENGTH ? ip.substring(0, MAX_IP_LENGTH) : ip;
    }
}
