package com.example.company.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员重置密码请求。newPassword 为空表示由系统生成一次性随机初始密码，
 * 该密码只在响应中返回一次，不落库、不写入审计。
 */
@Data
public class ResetPasswordRequest {

    @Size(min = 8, max = 64, message = "密码长度需为 8-64 位")
    private String newPassword;
}
