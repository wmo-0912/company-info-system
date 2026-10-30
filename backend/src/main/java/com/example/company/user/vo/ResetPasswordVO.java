package com.example.company.user.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ResetPasswordVO {

    private String username;

    /** 本次生效的密码（管理员指定或系统生成），仅在此响应中出现一次 */
    private String initialPassword;
}
