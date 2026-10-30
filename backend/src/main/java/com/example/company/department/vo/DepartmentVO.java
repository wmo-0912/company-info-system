package com.example.company.department.vo;

import lombok.Data;

import java.util.List;

@Data
public class DepartmentVO {

    private Long id;
    private String departmentCode;
    private String departmentName;
    private Long parentId;
    private Long managerEmployeeId;
    private String managerEmployeeName;
    private String description;
    private String status;
    private List<DepartmentVO> children;
}
