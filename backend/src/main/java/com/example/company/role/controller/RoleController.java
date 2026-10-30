package com.example.company.role.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.company.audit.service.AuditService;
import com.example.company.common.api.ApiResponse;
import com.example.company.common.exception.BusinessException;
import com.example.company.role.dto.RoleRequest;
import com.example.company.role.entity.SysPermission;
import com.example.company.role.entity.SysRole;
import com.example.company.role.entity.SysRolePermission;
import com.example.company.role.mapper.SysPermissionMapper;
import com.example.company.role.mapper.SysRoleMapper;
import com.example.company.role.mapper.SysRolePermissionMapper;
import com.example.company.role.vo.PermissionTreeVO;
import com.example.company.role.vo.RoleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Tag(name = "角色与权限")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RoleController {

    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final AuditService auditService;

    @Operation(summary = "角色列表")
    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('role:read')")
    public ApiResponse<List<RoleVO>> listRoles() {
        List<SysRole> roles = roleMapper.selectList(new QueryWrapper<SysRole>().orderByAsc("id"));
        return ApiResponse.ok(roles.stream().map(this::toVO).toList());
    }

    @Operation(summary = "创建角色")
    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('role:manage')")
    @Transactional
    public ApiResponse<RoleVO> createRole(@Valid @RequestBody RoleRequest request) {
        if (roleMapper.selectCount(new QueryWrapper<SysRole>().eq("role_code", request.getRoleCode())) > 0) {
            throw BusinessException.conflict("角色编码已存在: " + request.getRoleCode());
        }
        SysRole role = new SysRole();
        role.setRoleCode(request.getRoleCode());
        role.setRoleName(request.getRoleName());
        role.setDescription(request.getDescription());
        role.setDataScope(request.getDataScope());
        role.setEnabled(true);
        roleMapper.insert(role);
        auditService.success("role:create", "role", role.getId(), "创建角色 " + role.getRoleCode());
        return ApiResponse.ok(toVO(role));
    }

    @Operation(summary = "更新角色")
    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('role:manage')")
    @Transactional
    public ApiResponse<RoleVO> updateRole(@PathVariable Long id, @Valid @RequestBody RoleRequest request) {
        SysRole role = requireRole(id);
        if (roleMapper.selectCount(new QueryWrapper<SysRole>()
                .eq("role_code", request.getRoleCode()).ne("id", id)) > 0) {
            throw BusinessException.conflict("角色编码已存在: " + request.getRoleCode());
        }
        role.setRoleCode(request.getRoleCode());
        role.setRoleName(request.getRoleName());
        role.setDescription(request.getDescription());
        role.setDataScope(request.getDataScope());
        roleMapper.updateById(role);
        auditService.success("role:update", "role", id, "更新角色 " + role.getRoleCode());
        return ApiResponse.ok(toVO(role));
    }

    @Operation(summary = "保存角色权限")
    @PutMapping("/roles/{id}/permissions")
    @PreAuthorize("hasAuthority('role:manage')")
    @Transactional
    public ApiResponse<Void> savePermissions(@PathVariable Long id, @Valid @RequestBody PermissionsBody body) {
        requireRole(id);
        rolePermissionMapper.delete(new QueryWrapper<SysRolePermission>().eq("role_id", id));
        for (Long permissionId : body.getPermissionIds()) {
            if (permissionMapper.selectById(permissionId) == null) {
                throw BusinessException.badRequest("权限不存在: " + permissionId);
            }
            SysRolePermission rp = new SysRolePermission();
            rp.setRoleId(id);
            rp.setPermissionId(permissionId);
            rolePermissionMapper.insert(rp);
        }
        auditService.success("role:permissions", "role", id, "更新角色权限");
        return ApiResponse.ok();
    }

    @Operation(summary = "权限树")
    @GetMapping("/permissions/tree")
    @PreAuthorize("hasAuthority('role:read') or hasAuthority('permission:manage')")
    public ApiResponse<List<PermissionTreeVO>> permissionTree() {
        List<SysPermission> all = permissionMapper.selectList(
                new QueryWrapper<SysPermission>().orderByAsc("sort_order", "id"));
        Map<Long, PermissionTreeVO> nodes = all.stream().map(this::toTreeVO)
                .collect(Collectors.toMap(PermissionTreeVO::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        List<PermissionTreeVO> roots = new ArrayList<>();
        for (PermissionTreeVO node : nodes.values()) {
            if (node.getParentId() != null && nodes.containsKey(node.getParentId())) {
                nodes.get(node.getParentId()).getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return ApiResponse.ok(roots);
    }

    private SysRole requireRole(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw BusinessException.notFound("角色不存在");
        }
        return role;
    }

    private RoleVO toVO(SysRole role) {
        RoleVO vo = new RoleVO();
        vo.setId(role.getId());
        vo.setRoleCode(role.getRoleCode());
        vo.setRoleName(role.getRoleName());
        vo.setDescription(role.getDescription());
        vo.setDataScope(role.getDataScope());
        vo.setEnabled(role.getEnabled());
        vo.setPermissionIds(permissionMapper.selectByRoleId(role.getId()).stream()
                .map(SysPermission::getId).toList());
        return vo;
    }

    private PermissionTreeVO toTreeVO(SysPermission p) {
        PermissionTreeVO vo = new PermissionTreeVO();
        vo.setId(p.getId());
        vo.setPermissionCode(p.getPermissionCode());
        vo.setPermissionName(p.getPermissionName());
        vo.setPermissionType(p.getPermissionType());
        vo.setParentId(p.getParentId());
        vo.setRoutePath(p.getRoutePath());
        vo.setIcon(p.getIcon());
        vo.setSortOrder(p.getSortOrder());
        return vo;
    }

    @Data
    public static class PermissionsBody {
        @NotEmpty(message = "权限列表不能为空")
        private List<Long> permissionIds;
    }
}
