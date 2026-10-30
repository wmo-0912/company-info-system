package com.example.company.role;

import com.example.company.audit.service.AuditService;
import com.example.company.common.exception.BusinessException;
import com.example.company.role.controller.RoleController;
import com.example.company.role.dto.RoleRequest;
import com.example.company.role.entity.SysPermission;
import com.example.company.role.entity.SysRole;
import com.example.company.role.mapper.SysPermissionMapper;
import com.example.company.role.mapper.SysRoleMapper;
import com.example.company.role.mapper.SysRolePermissionMapper;
import com.example.company.role.vo.RoleVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleControllerTest {

    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SysPermissionMapper permissionMapper;
    @Mock
    private SysRolePermissionMapper rolePermissionMapper;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private RoleController controller;

    private SysRole role(Long id, String code, boolean enabled, String dataScope) {
        SysRole r = new SysRole();
        r.setId(id);
        r.setRoleCode(code);
        r.setRoleName(code);
        r.setEnabled(enabled);
        r.setDataScope(dataScope);
        return r;
    }

    private RoleController.EnabledBody enabledBody(boolean enabled) {
        RoleController.EnabledBody body = new RoleController.EnabledBody();
        body.setEnabled(enabled);
        return body;
    }

    private RoleController.PermissionsBody permissionBody(Long... ids) {
        RoleController.PermissionsBody body = new RoleController.PermissionsBody();
        body.setPermissionIds(List.of(ids));
        return body;
    }

    @Test
    void disableBuiltInRoleRejected() {
        when(roleMapper.selectById(1L)).thenReturn(role(1L, "HR", true, "ALL"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.setEnabled(1L, enabledBody(false)));

        assertEquals(400, ex.getHttpStatus());
        verify(roleMapper, never()).updateById(any(SysRole.class));
        verifyNoInteractions(auditService);
    }

    @Test
    void disableCustomRoleTogglesFlag() {
        when(roleMapper.selectById(9L)).thenReturn(role(9L, "AUDITOR", true, "SELF"));

        RoleVO vo = controller.setEnabled(9L, enabledBody(false)).getData();

        assertFalse(vo.getEnabled());
        verify(roleMapper).updateById(any(SysRole.class));
        verify(auditService).success(eq("role:enabled"), eq("role"), eq(9L), contains("停用"));
    }

    @Test
    void enablingAlreadyEnabledRoleSkipsWrite() {
        when(roleMapper.selectById(9L)).thenReturn(role(9L, "AUDITOR", true, "SELF"));

        RoleVO vo = controller.setEnabled(9L, enabledBody(true)).getData();

        assertTrue(vo.getEnabled());
        verify(roleMapper, never()).updateById(any(SysRole.class));
        verifyNoInteractions(auditService);
    }

    @Test
    void builtInRoleCodeCannotBeRenamed() {
        when(roleMapper.selectById(1L)).thenReturn(role(1L, "ADMIN", true, "ALL"));
        RoleRequest request = new RoleRequest();
        request.setRoleCode("ADMIN2");
        request.setRoleName("管理员");
        request.setDataScope("ALL");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.updateRole(1L, request));

        assertEquals(400, ex.getHttpStatus());
        verify(roleMapper, never()).updateById(any(SysRole.class));
    }

    @Test
    void adminRoleScopeMustStayAll() {
        when(roleMapper.selectById(1L)).thenReturn(role(1L, "ADMIN", true, "ALL"));
        RoleRequest request = new RoleRequest();
        request.setRoleCode("ADMIN");
        request.setRoleName("管理员");
        request.setDataScope("DEPARTMENT");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.updateRole(1L, request));

        assertEquals(400, ex.getHttpStatus());
        verify(roleMapper, never()).updateById(any(SysRole.class));
    }

    @Test
    void adminMustKeepRoleManagePermission() {
        when(roleMapper.selectById(1L)).thenReturn(role(1L, "ADMIN", true, "ALL"));
        SysPermission manage = new SysPermission();
        manage.setId(9L);
        when(permissionMapper.selectOne(any())).thenReturn(manage);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.savePermissions(1L, permissionBody(1L, 2L)));

        assertEquals(400, ex.getHttpStatus());
        verify(rolePermissionMapper, never()).delete(any());
    }

    @Test
    void adminKeepingRoleManagePermissionCanBeSaved() {
        when(roleMapper.selectById(1L)).thenReturn(role(1L, "ADMIN", true, "ALL"));
        SysPermission manage = new SysPermission();
        manage.setId(9L);
        when(permissionMapper.selectOne(any())).thenReturn(manage);
        when(permissionMapper.selectById(anyLong())).thenReturn(manage);

        assertDoesNotThrow(() -> controller.savePermissions(1L, permissionBody(9L)));

        verify(rolePermissionMapper).delete(any());
        verify(rolePermissionMapper).insert(any(com.example.company.role.entity.SysRolePermission.class));
    }
}
