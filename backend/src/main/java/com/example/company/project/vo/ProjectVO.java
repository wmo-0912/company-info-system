package com.example.company.project.vo;

import lombok.Data;

@Data
public class ProjectVO {

    private Long id;
    private String projectCode;
    private String projectName;
    private Long managerEmployeeId;
    private String managerEmployeeName;
    private String description;
    private String status;
    private Integer memberCount;
}
