package com.example.company.user;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.company.audit.service.AuditService;
import com.example.company.employee.entity.Employee;
import com.example.company.role.entity.SysRole;
import com.example.company.role.entity.SysUserRole;
import com.example.company.role.mapper.SysRoleMapper;
import com.example.company.role.mapper.SysUserRoleMapper;
import com.example.company.user.entity.SysUser;
import com.example.company.user.mapper.SysUserMapper;
import com.example.company.user.service.AccountProvisioningService;
import com.example.company.user.service.PasswordHistoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountProvisioningServiceTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PasswordHistoryService passwordHistoryService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private AccountProvisioningService service;

    private Employee employee(String employeeNo) {
        Employee e = new Employee();
        e.setId(5L);
        e.setEmployeeNo(employeeNo);
        e.setEmployeeName("赵六");
        return e;
    }

    private SysRole employeeRole() {
        SysRole role = new SysRole();
        role.setId(9L);
        role.setRoleCode("EMPLOYEE");
        return role;
    }

    @Test
    void provisionCreatesAccountWithEmployeeNumberAsUsername() {
        when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(roleMapper.selectOne(any(QueryWrapper.class))).thenReturn(employeeRole());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-initial");
        when(userMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
            SysUser u = invocation.getArgument(0);
            u.setId(77L);
            return 1;
        });

        AccountProvisioningService.Result result = service.provision(employee("E0201"));

        assertTrue(result.created());
        assertEquals("E0201", result.username());
        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(captor.capture());
        SysUser created = captor.getValue();
        assertEquals("E0201", created.getUsername());
        assertEquals("hashed-initial", created.getPasswordHash());
        assertEquals(5L, created.getEmployeeId());
        assertEquals("ACTIVE", created.getStatus());
        assertTrue(created.getMustChangePassword(), "初始密码必须强制首次登录修改");
        verify(passwordHistoryService).record(77L, "hashed-initial");
        ArgumentCaptor<SysUserRole> linkCaptor = ArgumentCaptor.forClass(SysUserRole.class);
        verify(userRoleMapper).insert(linkCaptor.capture());
        assertEquals(77L, linkCaptor.getValue().getUserId());
        assertEquals(9L, linkCaptor.getValue().getRoleId());
        verify(auditService).success(any(), any(), any(), any());
    }

    @Test
    void provisionSkipsWhenEmployeeAlreadyHasAccount() {
        when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);

        AccountProvisioningService.Result result = service.provision(employee("E0201"));

        assertFalse(result.created());
        assertTrue(result.detail().contains("已有登录账号"), result.detail());
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    void provisionSkipsWhenUsernameTaken() {
        when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L, 1L);

        AccountProvisioningService.Result result = service.provision(employee("E0201"));

        assertFalse(result.created());
        assertNull(result.username());
        assertTrue(result.detail().contains("已被其他账号占用"), result.detail());
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    void provisionSkipsWhenEmployeeRoleMissing() {
        when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(roleMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        AccountProvisioningService.Result result = service.provision(employee("E0201"));

        assertFalse(result.created());
        assertTrue(result.detail().contains("缺少 EMPLOYEE 角色"), result.detail());
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    void provisionSkipsWhenEmployeeNumberBlank() {
        AccountProvisioningService.Result result = service.provision(employee("   "));

        assertFalse(result.created());
        assertTrue(result.detail().contains("工号为空"), result.detail());
        verify(userMapper, never()).selectCount(any(QueryWrapper.class));
    }

    @Test
    void provisionSkipsWhenEmployeeMissing() {
        AccountProvisioningService.Result result = service.provision(null);

        assertFalse(result.created());
        assertTrue(result.detail().contains("档案不存在"), result.detail());
    }
}
