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

    private OffsetDateTime lastLoginAt;

    private Integer failedLoginCount;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    @TableLogic
    private Boolean deleted;
}
