package com.example.company.employment.vo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class EmploymentRecordVO {

    private Long id;
    private Long employeeId;
    private String changeType;
    private LocalDate effectiveDate;
    private Long oldDepartmentId;
    private String oldDepartmentName;
    private Long newDepartmentId;
    private String newDepartmentName;
    private String oldPositionName;
    private String newPositionName;
    private String reason;
    private String operatorName;
}
