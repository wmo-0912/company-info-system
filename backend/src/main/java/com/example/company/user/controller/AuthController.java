package com.example.company.user.controller;

import com.example.company.audit.service.AuditService;
import com.example.company.common.api.ApiResponse;
import com.example.company.common.exception.BusinessException;
import com.example.company.common.security.LoginRateLimiter;
import com.example.company.common.web.ClientIp;
import com.example.company.common.security.SecurityUtils;
import com.example.company.user.dto.LoginRequest;
import com.example.company.user.service.UserService;
import com.example.company.user.vo.CurrentUserVO;
import com.example.company.user.vo.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "认证")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuditService auditService;
    private final LoginRateLimiter loginRateLimiter;

    @Operation(summary = "登录")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        // 按 IP 限流：同一来源 5 分钟内失败 10 次即 429，避免暴力尝试无限消耗失败计数
        String ip = ClientIp.resolve(http);
        loginRateLimiter.assertAllowed(ip);
        try {
            LoginResponse response = userService.login(request);
            loginRateLimiter.recordSuccess(ip);
            return ApiResponse.ok(response);
        } catch (BusinessException e) {
            if (e.getHttpStatus() == 401) {
                loginRateLimiter.recordFailure(ip);
            }
            throw e;
        }
    }

    @Operation(summary = "当前用户")
    @GetMapping("/me")
    public ApiResponse<CurrentUserVO> me() {
        return ApiResponse.ok(userService.currentUser(SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "退出登录（客户端清除 token；本系统采用无状态 JWT，服务端不做即时吊销）")
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        auditService.success("auth:logout", "user", SecurityUtils.getCurrentUserId(), "退出登录");
        return ApiResponse.ok();
    }
}
