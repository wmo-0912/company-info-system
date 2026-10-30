package com.example.company.performance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@TableName("performance_review")
public class PerformanceReview {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long employeeId;

    private Long reviewerEmployeeId;

    private Long projectTeamId;

    private String periodType;

    private String periodKey;

    private BigDecimal score;

    private String rating;

    private String comment;

    private String status;

    private OffsetDateTime submittedAt;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
