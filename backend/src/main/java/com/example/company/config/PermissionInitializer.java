package com.example.company.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.company.role.entity.SysPermission;
import com.example.company.role.entity.SysRole;
import com.example.company.role.entity.SysRolePermission;
import com.example.company.role.mapper.SysPermissionMapper;
import com.example.company.role.mapper.SysRoleMapper;
import com.example.company.role.mapper.SysRolePermissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置权限点与四角色权限矩阵同步（幂等，默认关闭）。
 *
 * <p>只维护 {@code sys_permission} 与四个内置角色（ADMIN / HR / PROJECT_MANAGER / EMPLOYEE）的
 * 权限绑定，<b>不创建任何账号、员工、部门或示例业务数据</b>——账号由管理员在「用户管理」中创建，
 * 或由运维直接在数据库里维护；本类不含任何口令常量。
 *
 * <p>开启方式：{@code INIT_PERMISSIONS=true}（或 {@code app.init-permissions=true}），
 * 仅在空库首次部署时执行一次即可；重复执行结果一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionInitializer implements CommandLineRunner {

    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysRolePermissionMapper rolePermissionMapper;

    @Value("${app.init-permissions:false}")
    private boolean initPermissions;

    @Override
    @Transactional
    public void run(String... args) {
        if (!initPermissions) {
            log.debug("未开启内置权限点同步（INIT_PERMISSIONS=false），跳过");
            return;
        }
        syncPermissions();
    }

    /**
     * 同步权限点与四角色权限矩阵（幂等）。
     * 权限点按 permission_code upsert；四角色按 role_code upsert；
     * 角色-权限绑定先删后插，确保与 D01 规格完全一致。
     */
    private void syncPermissions() {
        record Perm(String code, String name, String type, String parent, String route, String icon, int sort) {}
        List<Perm> defs = List.of(
                new Perm("dashboard:view", "首页仪表盘", "MENU", null, "/", "Odometer", 1),
                new Perm("organization", "组织架构", "MENU", null, null, "OfficeBuilding", 2),
                new Perm("department:read", "部门管理", "MENU", "organization", "/departments", null, 21),
                new Perm("department:create", "部门新增", "BUTTON", "department:read", null, null, 22),
                new Perm("department:update", "部门编辑", "BUTTON", "department:read", null, null, 23),
                new Perm("department:disable", "部门停用", "BUTTON", "department:read", null, null, 24),
                new Perm("employee:read", "员工档案", "MENU", "organization", "/employees", null, 31),
                new Perm("employee:create", "员工新增", "BUTTON", "employee:read", null, null, 32),
                new Perm("employee:update", "员工编辑", "BUTTON", "employee:read", null, null, 33),
                new Perm("employee:archive", "员工归档", "BUTTON", "employee:read", null, null, 34),
                new Perm("employment:submit", "入转调离", "MENU", null, "/employment", "Switch", 4),
                new Perm("employment:process", "入转调离办理", "BUTTON", "employment:submit", null, null, 41),
                new Perm("project:read", "项目组", "MENU", null, "/projects", "Folder", 5),
                new Perm("project:create", "项目组新增", "BUTTON", "project:read", null, null, 51),
                new Perm("project:update", "项目组编辑", "BUTTON", "project:read", null, null, 52),
                new Perm("project:member:manage", "成员管理", "BUTTON", "project:read", null, null, 53),
                new Perm("performance:read", "绩效考核", "MENU", null, "/performance", "TrendCharts", 6),
                new Perm("performance:create", "绩效创建", "BUTTON", "performance:read", null, null, 61),
                new Perm("performance:update", "绩效编辑", "BUTTON", "performance:read", null, null, 62),
                new Perm("performance:submit", "绩效提交", "BUTTON", "performance:read", null, null, 63),
                new Perm("performance:review", "绩效复核", "BUTTON", "performance:read", null, null, 64),
                new Perm("system", "系统管理", "MENU", null, null, "Setting", 7),
                new Perm("user:read", "用户管理", "MENU", "system", "/system/users", null, 71),
                new Perm("user:create", "用户新增", "BUTTON", "user:read", null, null, 72),
                new Perm("user:update", "用户编辑", "BUTTON", "user:read", null, null, 73),
                new Perm("user:disable", "用户启停", "BUTTON", "user:read", null, null, 74),
                new Perm("role:read", "角色管理", "MENU", "system", "/system/roles", null, 75),
                new Perm("role:manage", "角色管理", "BUTTON", "role:read", null, null, 76),
                new Perm("permission:manage", "权限点管理", "BUTTON", "role:read", null, null, 77),
                new Perm("dictionary:read", "数据字典", "MENU", "system", "/system/dictionaries", null, 78),
                new Perm("dictionary:manage", "字典管理", "BUTTON", "dictionary:read", null, null, 79),
                new Perm("system:config:read", "系统配置", "MENU", "system", "/system/config", null, 80),
                new Perm("system:config:update", "配置更新", "BUTTON", "system:config:read", null, null, 81),
                new Perm("audit:read", "审计日志", "MENU", "system", "/system/audit-logs", null, 82),
                new Perm("profile:read", "个人中心", "MENU", null, "/profile", "Postcard", 8),
                new Perm("profile:update", "个人资料修改", "BUTTON", "profile:read", null, null, 83)
        );

        // 1. upsert 权限点
        Map<String, Long> permIds = new HashMap<>();
        for (Perm def : defs) {
            SysPermission existing = permissionMapper.selectOne(
                    new QueryWrapper<SysPermission>().eq("permission_code", def.code()));
            Long parentId = def.parent() == null ? null : permIds.get(def.parent());
            if (existing == null) {
                SysPermission p = new SysPermission();
                p.setPermissionCode(def.code());
                p.setPermissionName(def.name());
                p.setPermissionType(def.type());
                p.setParentId(parentId);
                p.setRoutePath(def.route());
                p.setIcon(def.icon());
                p.setSortOrder(def.sort());
                p.setEnabled(true);
                permissionMapper.insert(p);
                permIds.put(def.code(), p.getId());
            } else {
                existing.setPermissionName(def.name());
                existing.setPermissionType(def.type());
                existing.setParentId(parentId);
                existing.setRoutePath(def.route());
                existing.setIcon(def.icon());
                existing.setSortOrder(def.sort());
                existing.setEnabled(true);
                permissionMapper.updateById(existing);
                permIds.put(def.code(), existing.getId());
            }
        }

        // 2. upsert 四角色（数据范围严格按 D01）
        SysRole admin = upsertRole("ADMIN", "管理员", "系统管理员", "ALL");
        SysRole hr = upsertRole("HR", "人事管理", "人事管理专员", "ALL");
        SysRole pm = upsertRole("PROJECT_MANAGER", "项目经理", "项目负责人", "PROJECT");
        SysRole empRole = upsertRole("EMPLOYEE", "普通员工", "普通员工", "SELF");

        // 3. 重绑角色-权限：先清除四角色旧绑定，再按规格矩阵写入
        List<Long> builtinRoleIds = List.of(admin.getId(), hr.getId(), pm.getId(), empRole.getId());
        rolePermissionMapper.delete(new QueryWrapper<SysRolePermission>().in("role_id", builtinRoleIds));

        // ADMIN：系统配置、权限管理（角色/用户/账号状态）、数据字典、组织架构、审计
        bindPerms(admin.getId(), List.of(
                "dashboard:view", "organization",
                "department:read", "department:create", "department:update", "department:disable",
                "employee:read",
                "user:read", "user:create", "user:update", "user:disable",
                "role:read", "role:manage", "permission:manage",
                "dictionary:read", "dictionary:manage",
                "performance:read", "performance:review",
                "system:config:read", "system:config:update",
                "audit:read",
                "profile:read", "profile:update"), permIds);

        // HR：员工档案管理、入转调离办理（发起并办理）
        bindPerms(hr.getId(), List.of(
                "dashboard:view", "organization",
                "department:read",
                "employee:read", "employee:create", "employee:update", "employee:archive",
                "employment:submit", "employment:process",
                "performance:read", "performance:review",
                "dictionary:read",
                "profile:read", "profile:update"), permIds);

        // PM：团队管理（查看所属项目组成员）、绩效/考核
        bindPerms(pm.getId(), List.of(
                "dashboard:view", "organization",
                "department:read", "employee:read",
                "project:read", "project:update", "project:member:manage",
                "performance:read", "performance:create", "performance:update", "performance:submit",
                "dictionary:read",
                "profile:read", "profile:update"), permIds);

        // EMPLOYEE：个人中心（查看/修改个人信息，查看自己的电子档案——含所在项目与本人绩效）
        bindPerms(empRole.getId(), List.of(
                "dashboard:view",
                "project:read", "performance:read",
                "dictionary:read",
                "profile:read", "profile:update"), permIds);

        log.info("已同步权限点({})与四角色权限矩阵（按 D01 规格）", permIds.size());
    }

    private SysRole upsertRole(String code, String name, String desc, String scope) {
        SysRole role = roleMapper.selectOne(new QueryWrapper<SysRole>().eq("role_code", code));
        if (role == null) {
            role = new SysRole();
            role.setRoleCode(code);
            role.setRoleName(name);
            role.setDescription(desc);
            role.setDataScope(scope);
            role.setEnabled(true);
            roleMapper.insert(role);
        } else {
            role.setRoleName(name);
            role.setDescription(desc);
            role.setDataScope(scope);
            role.setEnabled(true);
            roleMapper.updateById(role);
        }
        return role;
    }

    private void bindPerms(Long roleId, List<String> codes, Map<String, Long> permIds) {
        for (String code : codes) {
            Long pid = permIds.get(code);
            if (pid == null) {
                log.warn("权限点未定义，跳过角色绑定: {}", code);
                continue;
            }
            SysRolePermission rp = new SysRolePermission();
            rp.setRoleId(roleId);
            rp.setPermissionId(pid);
            rolePermissionMapper.insert(rp);
        }
    }
}
