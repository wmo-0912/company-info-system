package com.example.company.project.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProjectMemberRequest {

    @NotNull(message = "员工不能为空")
    private Long employeeId;

    @Size(max = 64)
    private String memberRole;
}
