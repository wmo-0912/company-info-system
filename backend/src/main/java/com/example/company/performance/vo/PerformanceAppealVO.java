package com.example.company.performance.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
public class PerformanceAppealVO {

    private Long id;
    private Long reviewId;
    private Long employeeId;
    private String employeeName;
    private Long reviewerEmployeeId;
    private String reviewerName;
    private String periodType;
    private String periodKey;
    private BigDecimal score;
    private String reason;
    private String status;
    private Long handlerEmployeeId;
    private String handlerName;
    private String handleComment;
    private OffsetDateTime handledAt;
    private OffsetDateTime createdAt;
}
