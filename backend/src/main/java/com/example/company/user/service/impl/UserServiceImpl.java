package com.example.company.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.company.common.enums.DataScopeType;
import com.example.company.common.exception.BusinessException;
import com.example.company.common.security.LoginUser;
import com.example.company.config.JwtTokenProvider;
import com.example.company.employee.entity.Employee;
import com.example.company.employee.mapper.EmployeeMapper;
import com.example.company.role.entity.SysPermission;
import com.example.company.role.entity.SysRole;
import com.example.company.role.mapper.SysPermissionMapper;
import com.example.company.role.mapper.SysRoleMapper;
import com.example.company.user.dto.LoginRequest;
import com.example.company.user.entity.SysUser;
import com.example.company.user.mapper.SysUserMapper;
import com.example.company.user.service.UserService;
import com.example.company.user.vo.CurrentUserVO;
import com.example.company.user.vo.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final EmployeeMapper employeeMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Override
    public LoginUser loadLoginUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            return null;
        }
        return buildLoginUser(user);
    }

    private LoginUser buildLoginUser(SysUser user) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setEmployeeId(user.getEmployeeId());

        List<SysRole> roles = roleMapper.selectByUserId(user.getId());
        loginUser.setRoles(roles.stream().map(SysRole::getRoleCode).collect(Collectors.toSet()));
        loginUser.setDataScope(roles.stream()
                .map(SysRole::getDataScope)
                .max(Comparator.comparingInt(this::scopeRank))
                .orElse(DataScopeType.SELF.name()));

        List<SysPermission> permissions = permissionMapper.selectByUserId(user.getId());
        loginUser.setPermissions(permissions.stream()
                .map(SysPermission::getPermissionCode)
                .collect(Collectors.toSet()));

        String displayName = user.getUsername();
        if (user.getEmployeeId() != null) {
            Employee employee = employeeMapper.selectById(user.getEmployeeId());
            if (employee != null) {
                displayName = employee.getEmployeeName();
            }
        }
        loginUser.setDisplayName(displayName);
        return loginUser;
    }

    private int scopeRank(String scope) {
        if (scope == null) return 0;
        return switch (DataScopeType.valueOf(scope)) {
            case SELF -> 0;
            case PROJECT -> 1;
            case DEPARTMENT -> 2;
            case ALL -> 3;
        };
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        SysUser user = userMapper.selectOne(new QueryWrapper<SysUser>().eq("username", request.getUsername()));
        // 统一错误提示，不泄露用户名是否存在
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            if (user != null) {
                user.setFailedLoginCount(user.getFailedLoginCount() + 1);
                if (user.getFailedLoginCount() >= MAX_FAILED_ATTEMPTS) {
                    user.setStatus("LOCKED");
                }
                userMapper.updateById(user);
            }
            throw BusinessException.unauthorized("用户名或密码错误");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw BusinessException.unauthorized("账号已被停用或锁定，请联系管理员");
        }

        user.setFailedLoginCount(0);
        user.setLastLoginAt(OffsetDateTime.now());
        userMapper.updateById(user);

        LoginUser loginUser = buildLoginUser(user);
        LoginResponse response = new LoginResponse();
        response.setToken(tokenProvider.generateToken(user.getId(), user.getUsername()));
        response.setExpiresAt(tokenProvider.expiresAt());
        response.setUser(toCurrentUserVO(loginUser));
        return response;
    }

    @Override
    public CurrentUserVO currentUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.unauthorized("用户不存在");
        }
        return toCurrentUserVO(buildLoginUser(user));
    }

    private CurrentUserVO toCurrentUserVO(LoginUser loginUser) {
        CurrentUserVO vo = new CurrentUserVO();
        vo.setId(loginUser.getUserId());
        vo.setUsername(loginUser.getUsername());
        vo.setDisplayName(loginUser.getDisplayName());
        vo.setEmployeeId(loginUser.getEmployeeId());
        vo.setRoles(loginUser.getRoles());
        vo.setPermissions(loginUser.getPermissions());
        vo.setDataScope(loginUser.getDataScope());
        return vo;
    }
}
