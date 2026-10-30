package com.example.company.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.company.audit.service.AuditService;
import com.example.company.common.api.ApiResponse;
import com.example.company.common.api.PageResponse;
import com.example.company.common.exception.BusinessException;
import com.example.company.employee.entity.Employee;
import com.example.company.employee.mapper.EmployeeMapper;
import com.example.company.role.entity.SysRole;
import com.example.company.role.entity.SysUserRole;
import com.example.company.role.mapper.SysRoleMapper;
import com.example.company.role.mapper.SysUserRoleMapper;
import com.example.company.user.dto.ResetPasswordRequest;
import com.example.company.user.dto.UserCreateRequest;
import com.example.company.user.entity.SysUser;
import com.example.company.user.mapper.SysUserMapper;
import com.example.company.user.service.PasswordHistoryService;
import com.example.company.user.service.UserService;
import com.example.company.user.vo.ResetPasswordVO;
import com.example.company.user.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final EmployeeMapper employeeMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final UserService userService;
    private final PasswordHistoryService passwordHistoryService;

    @Operation(summary = "用户分页列表")
    @GetMapping
    @PreAuthorize("hasAuthority('user:read')")
    public ApiResponse<PageResponse<UserVO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        QueryWrapper<SysUser> qw = new QueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.like("username", keyword);
        }
        if (StringUtils.hasText(status)) {
            qw.eq("status", status);
        }
        qw.orderByAsc("id");
        Page<SysUser> result = userMapper.selectPage(new Page<>(page, pageSize), qw);
        return ApiResponse.ok(PageResponse.from(result, this::toVO));
    }

    @Operation(summary = "用户详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user:read')")
    public ApiResponse<UserVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(toVO(requireUser(id)));
    }

    @Operation(summary = "创建用户（初始密码由管理员线下交付，本系统不发送通知）")
    @PostMapping
    @PreAuthorize("hasAuthority('user:create')")
    @Transactional
    public ApiResponse<UserVO> create(@Valid @RequestBody UserCreateRequest request) {
        if (userMapper.selectCount(new QueryWrapper<SysUser>().eq("username", request.getUsername())) > 0) {
            throw BusinessException.conflict("用户名已存在: " + request.getUsername());
        }
        assertEmployeeAssignable(request.getEmployeeId(), null);
        List<SysRole> roles = requireRoles(request.getRoleIds());

        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEmployeeId(request.getEmployeeId());
        user.setStatus("ACTIVE");
        // 初始密码由管理员线下交付，首次登录必须自行修改
        user.setMustChangePassword(true);
        userMapper.insert(user);
        // 初始密码也进历史：用户首次改密后不能直接改回初始密码
        passwordHistoryService.record(user.getId(), user.getPasswordHash());
        saveUserRoles(user.getId(), roles.stream().map(SysRole::getId).toList());
        auditService.success("user:create", "user", user.getId(), "创建用户 " + user.getUsername());
        return ApiResponse.ok(toVO(user));
    }

    @Operation(summary = "更新用户（关联员工）")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('user:update')")
    @Transactional
    public ApiResponse<UserVO> update(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        SysUser user = requireUser(id);
        Long employeeId = body.get("employeeId");
        assertEmployeeAssignable(employeeId, id);
        user.setEmployeeId(employeeId);
        userMapper.updateById(user);
        auditService.success("user:update", "user", id, "更新用户关联员工");
        return ApiResponse.ok(toVO(user));
    }

    @Operation(summary = "启用/停用/解锁用户")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('user:disable')")
    @Transactional
    public ApiResponse<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody StatusBody body) {
        if (!List.of("ACTIVE", "DISABLED", "LOCKED").contains(body.getStatus())) {
            throw BusinessException.badRequest("非法的用户状态");
        }
        SysUser user = requireUser(id);
        user.setStatus(body.getStatus());
        if ("ACTIVE".equals(body.getStatus())) {
            user.setFailedLoginCount(0);
        }
        userMapper.updateById(user);
        auditService.success("user:status", "user", id, "用户状态调整为 " + body.getStatus());
        return ApiResponse.ok();
    }

    @Operation(summary = "重置用户密码（不传新密码则生成一次性随机密码，仅响应返回一次）")
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('user:disable')")
    public ApiResponse<ResetPasswordVO> resetPassword(@PathVariable Long id,
                                                     @RequestBody(required = false) @Valid ResetPasswordRequest request) {
        String newPassword = userService.resetPassword(id, request == null ? null : request.getNewPassword());
        String username = requireUser(id).getUsername();
        return ApiResponse.ok(new ResetPasswordVO(username, newPassword));
    }

    @Operation(summary = "分配角色")
    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('user:update')")
    @Transactional
    public ApiResponse<UserVO> assignRoles(@PathVariable Long id, @Valid @RequestBody RolesBody body) {
        SysUser user = requireUser(id);
        List<SysRole> roles = requireRoles(body.getRoleIds());
        userRoleMapper.delete(new QueryWrapper<SysUserRole>().eq("user_id", id));
        saveUserRoles(id, roles.stream().map(SysRole::getId).toList());
        auditService.success("user:roles", "user", id, "分配角色 " + body.getRoleIds());
        return ApiResponse.ok(toVO(user));
    }

    private void saveUserRoles(Long userId, List<Long> roleIds) {
        for (Long roleId : roleIds) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }
    }

    private void assertEmployeeAssignable(Long employeeId, Long excludeUserId) {
        if (employeeId == null) {
            return;
        }
        Employee employee = employeeMapper.selectById(employeeId);
        if (employee == null) {
            throw BusinessException.badRequest("关联员工不存在");
        }
        QueryWrapper<SysUser> qw = new QueryWrapper<SysUser>().eq("employee_id", employeeId);
        if (excludeUserId != null) {
            qw.ne("id", excludeUserId);
        }
        if (userMapper.selectCount(qw) > 0) {
            throw BusinessException.conflict("该员工已绑定其他账号");
        }
    }

    private List<SysRole> requireRoles(List<Long> roleIds) {
        List<SysRole> roles = roleMapper.selectBatchIds(roleIds);
        if (roles.size() != roleIds.stream().distinct().count()) {
            throw BusinessException.badRequest("存在无效角色");
        }
        return roles;
    }

    private SysUser requireUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        return user;
    }

    private UserVO toVO(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmployeeId(user.getEmployeeId());
        if (user.getEmployeeId() != null) {
            Employee employee = employeeMapper.selectById(user.getEmployeeId());
            vo.setEmployeeName(employee == null ? null : employee.getEmployeeName());
        }
        vo.setStatus(user.getStatus());
        vo.setLastLoginAt(user.getLastLoginAt());
        List<SysRole> roles = roleMapper.selectByUserId(user.getId());
        vo.setRoles(roles.stream().map(SysRole::getRoleCode).collect(Collectors.toList()));
        vo.setRoleIds(roles.stream().map(SysRole::getId).collect(Collectors.toList()));
        return vo;
    }

    @Data
    public static class StatusBody {
        @NotBlank(message = "状态不能为空")
        private String status;
    }

    @Data
    public static class RolesBody {
        @NotEmpty(message = "至少分配一个角色")
        private List<Long> roleIds;
    }
}
