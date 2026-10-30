package com.example.company.user.controller;

import com.example.company.common.api.ApiResponse;
import com.example.company.common.security.SecurityUtils;
import com.example.company.user.dto.LoginRequest;
import com.example.company.user.service.UserService;
import com.example.company.user.vo.CurrentUserVO;
import com.example.company.user.vo.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "认证")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @Operation(summary = "登录")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(userService.login(request));
    }

    @Operation(summary = "当前用户")
    @GetMapping("/me")
    public ApiResponse<CurrentUserVO> me() {
        return ApiResponse.ok(userService.currentUser(SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "退出登录（客户端清除 token；本系统采用无状态 JWT，服务端不做即时吊销）")
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.ok();
    }
}
