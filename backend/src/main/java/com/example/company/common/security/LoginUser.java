package com.example.company.common.security;

import lombok.Data;

import java.util.List;
import java.util.Set;

/**
 * 当前登录用户上下文，由 JWT 过滤器装载进 SecurityContext。
 */
@Data
public class LoginUser {

    private Long userId;

    private String username;

    /** 关联员工 ID，可为空（如纯系统管理员） */
    private Long employeeId;

    private String displayName;

    /** 角色编码集合 */
    private Set<String> roles;

    /** 权限点编码集合 */
    private Set<String> permissions;

    /** 最强数据范围（多角色取并集时取范围最大者） */
    private String dataScope;

    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
