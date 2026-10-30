package com.example.company.user.vo;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class LoginResponse {

    private String token;
    private String tokenType = "Bearer";
    private OffsetDateTime expiresAt;
    private CurrentUserVO user;
}
