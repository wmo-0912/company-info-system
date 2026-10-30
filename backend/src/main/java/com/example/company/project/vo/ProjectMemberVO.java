package com.example.company.project.vo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ProjectMemberVO {

    private Long id;
    private Long projectTeamId;
    private Long employeeId;
    private String employeeNo;
    private String employeeName;
    private String departmentName;
    private String memberRole;
    private LocalDate joinedAt;
    private LocalDate leftAt;
}
