package com.example.company.system.vo;

import java.util.List;

/**
 * 侧边栏菜单节点，由 sys_permission 中 permission_type = MENU 的权限点生成。
 * 前端只消费这个结构，不再维护一份硬编码菜单。
 */
public record MenuVO(
        Long id,
        String code,
        String title,
        String path,
        String icon,
        List<MenuVO> children) {
}
