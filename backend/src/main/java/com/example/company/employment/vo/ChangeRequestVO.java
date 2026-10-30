package com.example.company.employment.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

@Data
public class ChangeRequestVO {

    private Long id;
    private String requestNo;
    private Long employeeId;
    private String employeeName;
    private String employeeNo;
    private String changeType;
    private String status;
    private LocalDate effectiveDate;
    private Map<String, Object> payload;
    private String reason;
    private Long submittedBy;
    private String submittedByName;
    private Long processedBy;
    private String processedByName;
    private OffsetDateTime processedAt;
    private String processComment;
    private OffsetDateTime createdAt;
}
