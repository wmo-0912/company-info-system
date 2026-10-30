package com.example.company.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String passwordHash;

    private Long employeeId;

    private String status;

    /** 首次登录或管理员重置密码后为 true，需先修改密码才能继续使用 */
    private Boolean mustChangePassword;

    private OffsetDateTime lastLoginAt;

    private Integer failedLoginCount;

    /** 会话版本：密码修改/重置时自增，签发在旧版本上的 JWT 随即失效 */
    private Integer tokenVersion;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    @TableLogic
    private Boolean deleted;
}
