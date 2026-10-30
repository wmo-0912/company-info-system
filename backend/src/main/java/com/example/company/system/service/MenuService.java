package com.example.company.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.company.common.security.LoginUser;
import com.example.company.role.entity.SysPermission;
import com.example.company.role.mapper.SysPermissionMapper;
import com.example.company.system.vo.MenuVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按当前用户的权限点生成菜单树。
 *
 * <p>规则：菜单即权限点（permission_type = MENU，route_path 非空则为可点击项）。
 * 用户被授予某节点即显示该节点；分组本身未授权但子节点被授权时，分组照常显示，
 * 这样角色只需要绑定叶子节点即可得到完整的父级导航。</p>
 */
@Service
@RequiredArgsConstructor
public class MenuService {

    private final SysPermissionMapper permissionMapper;

    public List<MenuVO> menusOf(LoginUser user) {
        if (user == null) {
            return List.of();
        }
        Set<String> granted = user.getPermissions() == null ? Set.of() : user.getPermissions();
        List<SysPermission> all = permissionMapper.selectList(new QueryWrapper<SysPermission>()
                .eq("permission_type", "MENU")
                .eq("enabled", true)
                .orderByAsc("sort_order"));
        if (all == null || all.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (SysPermission p : all) {
            ids.add(p.getId());
        }
        Map<Long, List<SysPermission>> children = new LinkedHashMap<>();
        List<SysPermission> roots = new ArrayList<>();
        for (SysPermission p : all) {
            Long parentId = p.getParentId();
            // parent 不在本批菜单里（已停用/已删除/历史脏数据）时按根节点处理，避免整枝菜单消失
            if (parentId == null || !ids.contains(parentId)) {
                roots.add(p);
            } else {
                children.computeIfAbsent(parentId, key -> new ArrayList<>()).add(p);
            }
        }
        List<MenuVO> result = new ArrayList<>();
        for (SysPermission root : roots) {
            MenuVO vo = build(root, children, granted);
            if (vo != null) {
                result.add(vo);
            }
        }
        return result;
    }

    private MenuVO build(SysPermission node, Map<Long, List<SysPermission>> children, Set<String> granted) {
        List<MenuVO> childVOs = new ArrayList<>();
        for (SysPermission child : children.getOrDefault(node.getId(), List.of())) {
            MenuVO vo = build(child, children, granted);
            if (vo != null) {
                childVOs.add(vo);
            }
        }
        boolean authorized = node.getPermissionCode() != null && granted.contains(node.getPermissionCode());
        if (!authorized && childVOs.isEmpty()) {
            return null;
        }
        return new MenuVO(node.getId(), node.getPermissionCode(), node.getPermissionName(),
                node.getRoutePath(), node.getIcon(), List.copyOf(childVOs));
    }
}
