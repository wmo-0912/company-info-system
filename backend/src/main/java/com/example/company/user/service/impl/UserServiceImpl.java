package com.example.company.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.company.audit.service.AuditService;
import com.example.company.common.enums.DataScopeType;
import com.example.company.common.exception.BusinessException;
import com.example.company.common.security.LoginUser;
import com.example.company.common.security.PasswordPolicy;
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
import org.springframework.util.StringUtils;

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
    private final AuditService auditService;
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
        loginUser.setTokenVersion(user.getTokenVersion());
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
        loginUser.setMustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()));
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
    public LoginResponse login(LoginRequest request) {
        SysUser user = userMapper.selectOne(new QueryWrapper<SysUser>().eq("username", request.getUsername()));
        // 统一错误提示，不泄露用户名是否存在
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            // 失败计数使用独立 UPDATE 语句落库：本方法刻意不加事务，
            // 避免"随后抛异常导致计数与 LOCKED 状态回滚"（历史上锁定机制因此从未生效）。
            if (user != null) {
                userMapper.incrementFailedLogin(user.getId(), MAX_FAILED_ATTEMPTS);
                int failed = user.getFailedLoginCount() + 1;
                auditService.recordForActor(user.getId(), user.getUsername(), "auth:login", "user", user.getId(), false,
                        "登录失败，连续失败 " + failed + " 次" + (failed >= MAX_FAILED_ATTEMPTS ? "，账号已锁定" : ""));
            } else {
                auditService.recordForActor(null, request.getUsername(), "auth:login", "user", null, false,
                        "登录失败：用户名不存在");
            }
            throw BusinessException.unauthorized("用户名或密码错误");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            auditService.recordForActor(user.getId(), user.getUsername(), "auth:login", "user", user.getId(), false,
                    "登录被拒绝：账号状态为 " + user.getStatus());
            // 提示与"密码错误"保持一致：否则攻击者可以用正确密码探测账号是否存在/被锁，
            // 真实原因（DISABLED/LOCKED）只写进审计日志，管理员可查。
            throw BusinessException.unauthorized("用户名或密码错误");
        }

        userMapper.markLoginSuccess(user.getId());
        user.setFailedLoginCount(0);
        user.setLastLoginAt(OffsetDateTime.now());

        LoginUser loginUser = buildLoginUser(user);
        LoginResponse response = new LoginResponse();
        response.setToken(tokenProvider.generateToken(user.getId(), user.getUsername(), user.getTokenVersion()));
        response.setExpiresAt(tokenProvider.expiresAt());
        response.setUser(toCurrentUserVO(loginUser));
        auditService.recordForActor(user.getId(), user.getUsername(), "auth:login", "user", user.getId(), true, "登录成功");
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
        vo.setMustChangePassword(loginUser.isMustChangePassword());
        return vo;
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.unauthorized("用户不存在");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            auditService.recordForActor(user.getId(), user.getUsername(), "auth:change-password", "user", userId, false,
                    "修改密码失败：原密码不正确");
            throw BusinessException.badRequest("原密码不正确");
        }
        PasswordPolicy.validate(newPassword);
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw BusinessException.badRequest("新密码不能与原密码相同");
        }
        // 只更新密码相关列：避免把整实体（含 lastLoginAt 等）回写造成并发覆盖
        SysUser patch = new SysUser();
        patch.setId(userId);
        patch.setPasswordHash(passwordEncoder.encode(newPassword));
        patch.setMustChangePassword(false);
        userMapper.updateById(patch);
        // 改密后让所有旧 token（含当前会话）失效，必须重新登录
        userMapper.bumpTokenVersion(userId);
        auditService.success("auth:change-password", "user", userId, "本人修改登录密码，所有旧会话已失效");
    }

    @Override
    @Transactional
    public String resetPassword(Long userId, String newPassword) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        String effective = StringUtils.hasText(newPassword) ? newPassword : PasswordPolicy.generate();
        PasswordPolicy.validate(effective);
        SysUser patch = new SysUser();
        patch.setId(userId);
        patch.setPasswordHash(passwordEncoder.encode(effective));
        patch.setMustChangePassword(true);
        patch.setFailedLoginCount(0);
        userMapper.updateById(patch);
        // 管理员重置后，被重置用户原有的登录会话立即失效
        userMapper.bumpTokenVersion(userId);
        // 审计只记动作，绝不记录明文密码
        auditService.success("user:reset-password", "user", userId,
                "管理员重置密码" + (StringUtils.hasText(newPassword) ? "（指定新密码）" : "（系统生成随机密码）"));
        return effective;
    }
}
