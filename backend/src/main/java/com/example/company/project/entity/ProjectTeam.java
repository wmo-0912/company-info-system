package com.example.company.project.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("project_team")
public class ProjectTeam {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String projectCode;

    private String projectName;

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
