package com.example.company.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("sys_audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long actorUserId;

    private String actorName;

    private String action;

    private String resourceType;

    private String resourceId;

    private String result;

    private String ipAddress;

    private String summary;

    private OffsetDateTime createdAt;
}
