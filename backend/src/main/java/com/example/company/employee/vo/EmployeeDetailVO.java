package com.example.company.employee.vo;

import lombok.Data;

import java.time.LocalDate;

/** 员工详情视图；敏感字段按可见性规则填充 */
@Data
public class EmployeeDetailVO {

    private Long id;
    private String employeeNo;
    private String employeeName;
    private String gender;
    private LocalDate birthDate;
    private String phone;
    private String email;
    private String address;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String educationCode;
    private Long departmentId;
    private String departmentName;
    private String positionName;
    private String positionLevelCode;
    private Long managerEmployeeId;
    private String managerEmployeeName;
    private LocalDate hireDate;
    private String employmentStatus;
    private LocalDate resignationDate;
    private String remark;
}
