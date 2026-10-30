package com.example.company.employee.vo;

import lombok.Data;

import java.time.LocalDate;

/** 列表用员工视图，只含必要字段 */
@Data
public class EmployeeListItemVO {

    private Long id;
    private String employeeNo;
    private String employeeName;
    private String gender;
    private Long departmentId;
    private String departmentName;
    private String positionName;
    private String positionLevelCode;
    private String employmentStatus;
    private LocalDate hireDate;
}
