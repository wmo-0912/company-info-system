package com.example.company.system;

import com.example.company.common.security.LoginUser;
import com.example.company.role.entity.SysPermission;
import com.example.company.role.mapper.SysPermissionMapper;
import com.example.company.system.service.MenuService;
import com.example.company.system.vo.MenuVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuServiceTest {

    @Mock
    private SysPermissionMapper permissionMapper;

    @InjectMocks
    private MenuService menuService;

    private SysPermission perm(Long id, String code, String name, String parentCode, String route) {
        SysPermission p = new SysPermission();
        p.setId(id);
        p.setPermissionCode(code);
        p.setPermissionName(name);
        p.setPermissionType("MENU");
        p.setRoutePath(route);
        p.setIcon("Setting");
        p.setEnabled(true);
        p.setParentId(null);
        return p;
    }

    private LoginUser user(String... permissions) {
        LoginUser user = new LoginUser();
        user.setUserId(1L);
        user.setUsername("u");
        user.setPermissions(Set.of(permissions));
        return user;
    }

    private SysPermission child(SysPermission parent, Long id, String code, String name, String route) {
        SysPermission p = perm(id, code, name, null, route);
        p.setParentId(parent.getId());
        return p;
    }

    @Test
    @DisplayName("分组自身未授权但子菜单被授权时，分组仍然出现")
    void groupShowsWhenOnlyChildGranted() {
        SysPermission group = perm(10L, "system", "系统管理", null, null);
        SysPermission users = child(group, 11L, "user:read", "用户管理", "/system/users");
        when(permissionMapper.selectList(any())).thenReturn(List.of(group, users));

        List<MenuVO> menus = menuService.menusOf(user("user:read"));

        assertEquals(1, menus.size());
        assertEquals("system", menus.get(0).code());
        assertEquals(1, menus.get(0).children().size());
        assertEquals("/system/users", menus.get(0).children().get(0).path());
    }

    @Test
    @DisplayName("未授权的叶子菜单与整枝未授权的分组都不下发")
    void unauthorizedNodesHidden() {
        SysPermission group = perm(10L, "system", "系统管理", null, null);
        SysPermission users = child(group, 11L, "user:read", "用户管理", "/system/users");
        SysPermission roles = child(group, 12L, "role:read", "角色管理", "/system/roles");
        when(permissionMapper.selectList(any())).thenReturn(List.of(group, users, roles));

        assertTrue(menuService.menusOf(user("employee:read")).isEmpty());
    }

    @Test
    @DisplayName("父节点不在这批菜单里时按根节点处理，菜单枝不会整体消失")
    void orphanParentTreatedAsRoot() {
        SysPermission orphan = perm(11L, "user:read", "用户管理", null, "/system/users");
        orphan.setParentId(999L);
        when(permissionMapper.selectList(any())).thenReturn(List.of(orphan));

        List<MenuVO> menus = menuService.menusOf(user("user:read"));

        assertEquals(1, menus.size());
        assertEquals("/system/users", menus.get(0).path());
    }

    @Test
    @DisplayName("未登录上下文直接返回空菜单，不查库")
    void nullUserDoesNotQuery() {
        assertTrue(menuService.menusOf(null).isEmpty());
        verify(permissionMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("没有任何权限点时不下发任何菜单")
    void userWithoutPermissions() {
        when(permissionMapper.selectList(any())).thenReturn(List.of(perm(10L, "system", "系统管理", null, null)));

        assertTrue(menuService.menusOf(user()).isEmpty());
    }
}
