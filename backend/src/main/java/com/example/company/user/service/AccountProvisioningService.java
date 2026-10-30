package com.example.company.user.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.company.audit.service.AuditService;
import com.example.company.common.security.PasswordPolicy;
import com.example.company.employee.entity.Employee;
import com.example.company.role.entity.SysRole;
import com.example.company.role.entity.SysUserRole;
import com.example.company.role.mapper.SysRoleMapper;
import com.example.company.role.mapper.SysUserRoleMapper;
import com.example.company.user.entity.SysUser;
import com.example.company.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 入职流程自动开通账号。
 *
 * <p>入职批准后员工档案已经存在，账号必须以"用户名 = 工号"生成，初始密码随机且<b>不</b>在响应里返回：
 * 响应体会进日志/前端缓存，线下交付口令应走管理员在用户管理里的「重置密码」（一次性密码只回一次）。
 * 账号状态 ACTIVE、must_change_password = true，首次登录必须改密。</p>
 *
 * <p>所有失败都是"跳过 + 说明"而不是抛异常：入职批准是主业务，账号占用/角色缺失不该让已经生效的
 * 入职回滚，管理员可以在用户管理里手工建号。</p>
 */
@Service
@RequiredArgsConstructor
public class AccountProvisioningService {

    /** 入职默认授予的角色编码 */
    public static final String DEFAULT_ROLE_CODE = "EMPLOYEE";

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final PasswordHistoryService passwordHistoryService;
    private final AuditService auditService;

    /**
     * @param created 是否真的创建了账号
     * @param username 创建的账号名（未创建时为 null）
     * @param detail   供审计/接口说明使用的可读结果
     */
    public record Result(boolean created, String username, String detail) {
    }

    @Transactional
    public Result provision(Employee employee) {
        if (employee == null || employee.getId() == null) {
            return new Result(false, null, "员工档案不存在，未开通账号");
        }
        if (!StringUtils.hasText(employee.getEmployeeNo())) {
            return new Result(false, null, "员工工号为空，未开通账号");
        }
        Long bound = userMapper.selectCount(new QueryWrapper<SysUser>().eq("employee_id", employee.getId()));
        if (bound != null && bound > 0) {
            return new Result(false, null, "该员工已有登录账号，无需重复开通");
        }
        String username = employee.getEmployeeNo().trim();
        if (userMapper.selectCount(new QueryWrapper<SysUser>().eq("username", username)) > 0) {
            return new Result(false, null, "用户名 " + username + " 已被其他账号占用，未开通账号");
        }
        SysRole role = roleMapper.selectOne(new QueryWrapper<SysRole>()
                .eq("role_code", DEFAULT_ROLE_CODE).last("limit 1"));
        if (role == null) {
            return new Result(false, null, "系统缺少 " + DEFAULT_ROLE_CODE + " 角色，未开通账号");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(PasswordPolicy.generate()));
        user.setEmployeeId(employee.getId());
        user.setStatus("ACTIVE");
        // 初始密码由管理员通过「重置密码」线下交付，首次登录必须自行修改
        user.setMustChangePassword(true);
        userMapper.insert(user);
        passwordHistoryService.record(user.getId(), user.getPasswordHash());

        SysUserRole link = new SysUserRole();
        link.setUserId(user.getId());
        link.setRoleId(role.getId());
        userRoleMapper.insert(link);

        auditService.success("user:provision", "user", user.getId(),
                "入职流程开通账号 " + username + "（角色 " + DEFAULT_ROLE_CODE + "）");
        return new Result(true, username, "已开通账号 " + username);
    }
}
