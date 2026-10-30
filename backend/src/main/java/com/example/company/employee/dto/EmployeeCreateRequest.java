package com.example.company.employee.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class EmployeeCreateRequest {

    @NotBlank(message = "员工编号不能为空")
    @Size(max = 32)
    private String employeeNo;

    @NotBlank(message = "员工姓名不能为空")
    @Size(max = 100)
    private String employeeName;

    @Size(max = 20)
    private String gender;

    private LocalDate birthDate;

    @Size(max = 32)
    private String phone;

    @Email(message = "邮箱格式不正确")
    @Size(max = 120)
    private String email;

    @Size(max = 300)
    private String address;

    @Size(max = 100)
    private String emergencyContactName;

    @Size(max = 32)
    private String emergencyContactPhone;

    @Size(max = 64)
    private String educationCode;

    @NotNull(message = "部门不能为空")
    private Long departmentId;

    @Size(max = 100)
    private String positionName;

    @Size(max = 64)
    private String positionLevelCode;

    private Long managerEmployeeId;

    private LocalDate hireDate;

    @Size(max = 2000)
    private String remark;
}
