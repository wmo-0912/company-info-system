package com.example.company.user;

import com.example.company.audit.service.AuditService;
import com.example.company.common.exception.BusinessException;
import com.example.company.config.JwtTokenProvider;
import com.example.company.employee.mapper.EmployeeMapper;
import com.example.company.role.mapper.SysPermissionMapper;
import com.example.company.role.mapper.SysRoleMapper;
import com.example.company.user.dto.LoginRequest;
import com.example.company.user.entity.SysUser;
import com.example.company.user.mapper.SysUserMapper;
import com.example.company.user.service.impl.UserServiceImpl;
import com.example.company.user.vo.LoginResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 登录锁定回归测试。
 * 历史缺陷：login() 曾带 @Transactional，失败分支先累加计数再抛异常，事务回滚导致
 * failed_login_count 与 LOCKED 永远不会落库，锁定机制形同虚设。
 * 这里通过"方法上不得有 @Transactional"以及"必须调用原子自增语句"两条断言防止回归。
 */
@ExtendWith(MockitoExtension.class)
class LoginLockoutTest {

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

    private SysUser user(String status, int failedCount) {
        SysUser u = new SysUser();
        u.setId(7L);
        u.setUsername("zhangsan");
        u.setPasswordHash("bcrypt-hash");
        u.setStatus(status);
        u.setFailedLoginCount(failedCount);
        return u;
    }

    private LoginRequest request(String password) {
        LoginRequest r = new LoginRequest();
        r.setUsername("zhangsan");
        r.setPassword(password);
        return r;
    }

    @Test
    void loginMustNotRunInTransactionSoFailureCountPersists() throws Exception {
        assertNull(UserServiceImpl.class.getMethod("login", LoginRequest.class).getAnnotation(Transactional.class),
                "login() 若包事务，失败计数会随异常回滚，锁定机制将再次失效");
    }

    @Test
    void failedPasswordIncrementsCounterAtomicallyAndAudits() {
        when(userMapper.selectOne(any())).thenReturn(user("ACTIVE", 3));
        when(passwordEncoder.matches(eq("bad"), anyString())).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.login(request("bad")));
        assertEquals(401, ex.getHttpStatus());

        verify(userMapper).incrementFailedLogin(7L, 5);
        // 绝不使用"读实体再 updateById"的写法：并发会丢计数，且依赖事务
        verify(userMapper, never()).updateById(any(SysUser.class));

        ArgumentCaptor<String> summary = ArgumentCaptor.forClass(String.class);
        verify(auditService).recordForActor(eq(7L), eq("zhangsan"), eq("auth:login"), eq("user"),
                eq(7L), eq(false), summary.capture());
        assertTrue(summary.getValue().contains("连续失败 4 次"));
    }

    @Test
    void fifthFailureAuditMentionsLock() {
        when(userMapper.selectOne(any())).thenReturn(user("ACTIVE", 4));
        when(passwordEncoder.matches(eq("bad"), anyString())).thenReturn(false);

        assertThrows(BusinessException.class, () -> userService.login(request("bad")));

        ArgumentCaptor<String> summary = ArgumentCaptor.forClass(String.class);
        verify(auditService).recordForActor(eq(7L), eq("zhangsan"), eq("auth:login"), eq("user"),
                eq(7L), eq(false), summary.capture());
        assertTrue(summary.getValue().contains("账号已锁定"));
    }

    @Test
    void lockedAccountCannotLoginEvenWithCorrectPassword() {
        when(userMapper.selectOne(any())).thenReturn(user("LOCKED", 5));
        when(passwordEncoder.matches(eq("right"), anyString())).thenReturn(true);

        assertThrows(BusinessException.class, () -> userService.login(request("right")));
        verify(userMapper, never()).markLoginSuccess(anyLong());
        verify(auditService).recordForActor(eq(7L), eq("zhangsan"), eq("auth:login"), eq("user"),
                eq(7L), eq(false), contains("账号状态为 LOCKED"));
    }

    @Test
    void successfulLoginResetsCounterAndIssuesToken() {
        when(userMapper.selectOne(any())).thenReturn(user("ACTIVE", 2));
        when(passwordEncoder.matches(eq("right"), anyString())).thenReturn(true);
        when(roleMapper.selectByUserId(7L)).thenReturn(List.of());
        when(permissionMapper.selectByUserId(7L)).thenReturn(List.of());
        when(tokenProvider.generateToken(7L, "zhangsan")).thenReturn("signed-token");
        when(tokenProvider.expiresAt()).thenReturn(OffsetDateTime.now().plusHours(2));

        LoginResponse response = userService.login(request("right"));

        assertEquals("signed-token", response.getToken());
        verify(userMapper).markLoginSuccess(7L);
        verify(userMapper, never()).incrementFailedLogin(anyLong(), anyInt());
        verify(auditService).recordForActor(eq(7L), eq("zhangsan"), eq("auth:login"), eq("user"),
                eq(7L), eq(true), anyString());
    }
}
