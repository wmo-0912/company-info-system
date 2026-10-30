package com.example.company.user.service;

import com.example.company.common.security.LoginUser;
import com.example.company.user.dto.LoginRequest;
import com.example.company.user.vo.CurrentUserVO;
import com.example.company.user.vo.LoginResponse;

public interface UserService {

    /** 按用户 ID 装载登录上下文；用户不存在/被停用返回 null */
    LoginUser loadLoginUser(Long userId);

    LoginResponse login(LoginRequest request);

    CurrentUserVO currentUser(Long userId);

    /** 本人修改密码：校验原密码与复杂度，成功后清除强制改密标记 */
    void changePassword(Long userId, String oldPassword, String newPassword);

    /** 管理员重置密码：newPassword 为空时随机生成；返回本次生效的密码并置强制改密标记 */
    String resetPassword(Long userId, String newPassword);
}
