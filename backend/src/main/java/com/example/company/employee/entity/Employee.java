package com.example.company.employee.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@TableName("employee")
public class Employee {

    @TableId(type = IdType.AUTO)
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

    private String positionName;

    private String positionLevelCode;

    private Long managerEmployeeId;

    private LocalDate hireDate;

    private String employmentStatus;

    private LocalDate resignationDate;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    @TableLogic
    private Boolean deleted;
}
