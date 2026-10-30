package com.example.company.employment.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.example.company.config.PgJsonbTypeHandler;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

@Data
@TableName(value = "employee_change_request", autoResultMap = true)
public class EmployeeChangeRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String requestNo;

    private Long employeeId;

    private String changeType;

    private String status;

    private LocalDate effectiveDate;

    @TableField(typeHandler = PgJsonbTypeHandler.class)
    private Map<String, Object> payload;

    private String reason;

    private Long submittedBy;

    private Long processedBy;

    private OffsetDateTime processedAt;

    private String processComment;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
