package com.example.company.performance.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
public class PerformanceReviewVO {

    private Long id;
    private Long employeeId;
    private String employeeName;
    private String employeeNo;
    private Long reviewerEmployeeId;
    private String reviewerName;
    private Long projectTeamId;
    private String projectTeamName;
    private String periodType;
    private String periodKey;
    private BigDecimal score;
    private String rating;
    private String comment;
    private String status;
    private OffsetDateTime submittedAt;
    private OffsetDateTime createdAt;
}
