package com.example.company.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 历史密码指纹（只存 BCrypt 哈希，不存明文）。
 * 改密/重置时写入一条，只保留最近若干条，用于阻止"改回旧密码"的循环。
 */
@Data
@TableName("sys_password_history")
public class SysPasswordHistory {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String passwordHash;
    private OffsetDateTime createdAt;
}
