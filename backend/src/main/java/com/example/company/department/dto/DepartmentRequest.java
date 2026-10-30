package com.example.company.department.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DepartmentRequest {

    @NotBlank(message = "部门编码不能为空")
    @Size(max = 64)
    private String departmentCode;

    @NotBlank(message = "部门名称不能为空")
    @Size(max = 100)
    private String departmentName;

    private Long parentId;

    private Long managerEmployeeId;

    @Size(max = 500)
    private String description;
}
