package com.example.company.employment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@TableName("employee_employment_record")
public class EmployeeEmploymentRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long employeeId;

    private String changeType;

    private LocalDate effectiveDate;

    private Long oldDepartmentId;

    private Long newDepartmentId;

    private String oldPositionName;

    private String newPositionName;

    private String reason;

    private Long operatorUserId;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
