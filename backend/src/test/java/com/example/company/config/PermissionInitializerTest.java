package com.example.company.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.company.role.entity.SysPermission;
import com.example.company.role.entity.SysRole;
import com.example.company.role.entity.SysRolePermission;
import com.example.company.role.mapper.SysPermissionMapper;
import com.example.company.role.mapper.SysRoleMapper;
import com.example.company.role.mapper.SysRolePermissionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 内置权限点同步器：默认关闭、开启后只写权限点与角色，不涉及任何账号数据。
 */
@ExtendWith(MockitoExtension.class)
class PermissionInitializerTest {

    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SysPermissionMapper permissionMapper;
    @Mock
    private SysRolePermissionMapper rolePermissionMapper;
    @InjectMocks
    private PermissionInitializer initializer;

    private void enable() {
        ReflectionTestUtils.setField(initializer, "initPermissions", true);
    }

    @Test
    @DisplayName("默认不开启时完全不碰数据库")
    void disabledByDefaultDoesNothing() {
        initializer.run();

        verifyNoInteractions(roleMapper, permissionMapper, rolePermissionMapper);
    }

    @Test
    @DisplayName("开启后插入 36 个权限点、4 个内置角色与 57 条角色权限绑定")
    void enabledCreatesPermissionsAndRoles() {
        enable();
        AtomicLong permId = new AtomicLong(1000);
        when(permissionMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        when(permissionMapper.insert(any(SysPermission.class))).thenAnswer(inv -> {
            inv.getArgument(0, SysPermission.class).setId(permId.incrementAndGet());
            return 1;
        });
        AtomicLong roleId = new AtomicLong(10);
        when(roleMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        when(roleMapper.insert(any(SysRole.class))).thenAnswer(inv -> {
            inv.getArgument(0, SysRole.class).setId(roleId.incrementAndGet());
            return 1;
        });

        initializer.run();

        verify(permissionMapper, times(36)).insert(any(SysPermission.class));
        verify(roleMapper, times(4)).insert(any(SysRole.class));
        verify(rolePermissionMapper).delete(any(QueryWrapper.class));
        verify(rolePermissionMapper, times(57)).insert(any(SysRolePermission.class));
    }

    @Test
    @DisplayName("重复执行时权限点走更新而不是重复插入")
    void enabledIsIdempotentForExistingPermissions() {
        enable();
        SysPermission existing = new SysPermission();
        existing.setId(500L);
        when(permissionMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);
        SysRole role = new SysRole();
        role.setId(7L);
        when(roleMapper.selectOne(any(QueryWrapper.class))).thenReturn(role);

        initializer.run();

        verify(permissionMapper, times(36)).updateById(any(SysPermission.class));
        verify(permissionMapper, never()).insert(any(SysPermission.class));
        verify(roleMapper, never()).insert(any(SysRole.class));
        verify(roleMapper, times(4)).updateById(any(SysRole.class));
    }
}
