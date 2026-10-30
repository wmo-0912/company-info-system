package com.example.company.department.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("org_department")
public class Department {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String departmentCode;

    private String departmentName;

    private Long parentId;

    private Long managerEmployeeId;

    private String description;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    @TableLogic
    private Boolean deleted;
}
