package com.example.company.performance.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("performance_appeal")
public class PerformanceAppeal {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reviewId;

    private Long employeeId;

    private String reason;

    private String status;

    private Long handlerEmployeeId;

    private String handleComment;

    private OffsetDateTime handledAt;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
