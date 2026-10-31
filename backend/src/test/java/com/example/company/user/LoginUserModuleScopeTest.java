package com.example.company.user;

import com.example.company.audit.service.AuditService;
import com.example.company.common.security.LoginUser;
import com.example.company.config.JwtTokenProvider;
import com.example.company.employee.mapper.EmployeeMapper;
import com.example.company.role.entity.SysRole;
import com.example.company.role.entity.SysRoleModuleScope;
import com.example.company.role.mapper.SysPermissionMapper;
import com.example.company.role.mapper.SysRoleMapper;
import com.example.company.role.mapper.SysRoleModuleScopeMapper;
import com.example.company.user.entity.SysUser;
import com.example.company.user.mapper.SysUserMapper;
import com.example.company.user.service.PasswordHistoryService;
import com.example.company.user.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginUserModuleScopeTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SysRoleModuleScopeMapper roleModuleScopeMapper;
    @Mock
    private SysPermissionMapper permissionMapper;
    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditService auditService;
    @Mock
    private JwtTokenProvider tokenProvider;
    @Mock
    private PasswordHistoryService passwordHistoryService;

    @InjectMocks
    private UserServiceImpl userService;

    private SysUser user() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("hr");
        user.setEmployeeId(5L);
        user.setStatus("ACTIVE");
        user.setTokenVersion(0);
        user.setMustChangePassword(false);
        return user;
    }

    private SysRole role(Long id, String code, String dataScope) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setRoleCode(code);
        role.setDataScope(dataScope);
        return role;
    }

    private SysRoleModuleScope override(Long roleId, String module, String scope) {
        SysRoleModuleScope entity = new SysRoleModuleScope();
        entity.setRoleId(roleId);
        entity.setModuleCode(module);
        entity.setScopeType(scope);
        return entity;
    }

    @Test
    void loginUserCarriesWidestModuleScopeAcrossRoles() {
        when(userMapper.selectById(1L)).thenReturn(user());
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(
                role(1L, "HR", "DEPARTMENT"), role(2L, "PROJECT_MANAGER", "SELF")));
        when(roleModuleScopeMapper.selectList(any())).thenReturn(List.of(
                override(1L, "performance", "SELF"),
                override(2L, "PERFORMANCE", "DEPARTMENT"),
                override(1L, "PROJECT", "PROJECT")));
        when(permissionMapper.selectByUserId(1L)).thenReturn(List.of());

        LoginUser loginUser = userService.loadLoginUser(1L);

        assertEquals("DEPARTMENT", loginUser.getModuleScopes().get("PERFORMANCE"),
                "同一模块多角色配置取范围最大者");
        assertEquals("PROJECT", loginUser.getModuleScopes().get("PROJECT"));
        assertEquals("DEPARTMENT", loginUser.getDataScope(), "角色级默认范围不受模块配置影响");
    }

    @Test
    void unknownModuleCodeIsIgnored() {
        when(userMapper.selectById(1L)).thenReturn(user());
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(role(1L, "HR", "SELF")));
        when(roleModuleScopeMapper.selectList(any())).thenReturn(List.of(
                override(1L, "SALARY", "ALL"),
                override(1L, "EMPLOYEE", "DEPARTMENT")));
        when(permissionMapper.selectByUserId(1L)).thenReturn(List.of());

        LoginUser loginUser = userService.loadLoginUser(1L);

        assertEquals(Map.of("EMPLOYEE", "DEPARTMENT"), loginUser.getModuleScopes());
    }

    @Test
    void illegalRoleScopeDoesNotBreakLogin() {
        when(userMapper.selectById(1L)).thenReturn(user());
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(role(1L, "HR", "TEAM_X")));
        when(roleModuleScopeMapper.selectList(any())).thenReturn(List.of());
        when(permissionMapper.selectByUserId(1L)).thenReturn(List.of());

        LoginUser loginUser = userService.loadLoginUser(1L);

        assertEquals("TEAM_X", loginUser.getDataScope(),
                "角色级取值保留原样，由 DataScopeService 按 SELF 兜底");
        assertTrue(loginUser.getModuleScopes().isEmpty());
    }

    @Test
    void disabledUserHasNoLoginUser() {
        SysUser disabled = user();
        disabled.setStatus("DISABLED");
        when(userMapper.selectById(1L)).thenReturn(disabled);

        assertNull(userService.loadLoginUser(1L));
    }
}
