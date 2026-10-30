package com.example.company.user;

import com.example.company.audit.service.AuditService;
import com.example.company.common.exception.BusinessException;
import com.example.company.config.JwtTokenProvider;
import com.example.company.employee.mapper.EmployeeMapper;
import com.example.company.role.mapper.SysPermissionMapper;
import com.example.company.role.mapper.SysRoleMapper;
import com.example.company.user.entity.SysUser;
import com.example.company.user.mapper.SysUserMapper;
import com.example.company.user.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordChangeTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysRoleMapper roleMapper;
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

    @InjectMocks
    private UserServiceImpl userService;

    private SysUser user() {
        SysUser u = new SysUser();
        u.setId(9L);
        u.setUsername("zhangsan");
        u.setPasswordHash("old-hash");
        u.setStatus("ACTIVE");
        u.setFailedLoginCount(0);
        return u;
    }

    @Test
    void changePasswordRejectsWrongOldPasswordAndAuditsFailure() {
        when(userMapper.selectById(9L)).thenReturn(user());
        when(passwordEncoder.matches(eq("wrong"), eq("old-hash"))).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.changePassword(9L, "wrong", "NewPass123"));
        assertEquals(400, ex.getHttpStatus());

        verify(userMapper, never()).updateById(any(SysUser.class));
        verify(auditService).recordForActor(eq(9L), eq("zhangsan"), eq("auth:change-password"), eq("user"),
                eq(9L), eq(false), contains("原密码不正确"));
    }

    @Test
    void changePasswordUpdatesHashAndClearsFlag() {
        when(userMapper.selectById(9L)).thenReturn(user());
        when(passwordEncoder.matches(eq("OldPass123"), eq("old-hash"))).thenReturn(true);
        when(passwordEncoder.matches(eq("NewPass123"), eq("old-hash"))).thenReturn(false);
        when(passwordEncoder.encode("NewPass123")).thenReturn("new-hash");

        userService.changePassword(9L, "OldPass123", "NewPass123");

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        SysUser patch = captor.getValue();
        assertEquals(9L, patch.getId());
        assertEquals("new-hash", patch.getPasswordHash());
        assertFalse(patch.getMustChangePassword(), "改密成功后必须清除强制改密标记");
        assertNull(patch.getLastLoginAt(), "补丁对象不应覆盖登录时间等无关列");
        verify(auditService).success(eq("auth:change-password"), eq("user"), eq(9L), anyString());
        // 改密后旧 token 必须立即失效：会话版本自增
        verify(userMapper).bumpTokenVersion(9L);
    }

    @Test
    void changePasswordRejectsWeakOrReusedPassword() {
        when(userMapper.selectById(9L)).thenReturn(user());
        when(passwordEncoder.matches(eq("OldPass123"), eq("old-hash"))).thenReturn(true);

        assertThrows(BusinessException.class, () -> userService.changePassword(9L, "OldPass123", "short1"));
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    void resetPasswordGeneratesRandomOneWhenBlankAndForcesChange() {
        when(userMapper.selectById(9L)).thenReturn(user());
        when(passwordEncoder.encode(anyString())).thenReturn("reset-hash");

        String generated = userService.resetPassword(9L, null);

        assertEquals(12, generated.length());
        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals("reset-hash", captor.getValue().getPasswordHash());
        assertTrue(captor.getValue().getMustChangePassword(), "重置后必须强制改密");
        assertEquals(0, captor.getValue().getFailedLoginCount());
        // 审计摘要不得包含明文密码
        verify(auditService).success(eq("user:reset-password"), eq("user"), eq(9L),
                argThat(s -> s != null && !s.contains(generated)));
        // 管理员重置后，被重置用户的既有会话必须失效
        verify(userMapper).bumpTokenVersion(9L);
    }
}
