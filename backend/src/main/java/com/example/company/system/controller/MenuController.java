package com.example.company.system.controller;

import com.example.company.common.api.ApiResponse;
import com.example.company.common.security.SecurityUtils;
import com.example.company.system.service.MenuService;
import com.example.company.system.vo.MenuVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 当前用户可见的菜单树。任何已登录用户都可调用，返回内容完全由自身的权限点决定。
 */
@RestController
@RequestMapping("/api/v1/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<MenuVO>> myMenus() {
        return ApiResponse.ok(menuService.menusOf(SecurityUtils.getCurrentUser()));
    }
}
