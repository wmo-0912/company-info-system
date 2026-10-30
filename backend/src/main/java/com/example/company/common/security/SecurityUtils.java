package com.example.company.common.security;

import com.example.company.common.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static LoginUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser loginUser)) {
            throw BusinessException.unauthorized("未登录或登录已失效");
        }
        return loginUser;
    }

    public static LoginUser getCurrentUserOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        return null;
    }

    public static Long getCurrentUserId() {
        return getCurrentUser().getUserId();
    }

    public static Long getCurrentEmployeeId() {
        return getCurrentUser().getEmployeeId();
    }

    public static void requirePermission(String permission) {
        if (!getCurrentUser().hasPermission(permission)) {
            throw BusinessException.forbidden("无权限执行该操作: " + permission);
        }
    }
}
