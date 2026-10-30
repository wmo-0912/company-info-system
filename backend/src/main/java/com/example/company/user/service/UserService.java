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
}
