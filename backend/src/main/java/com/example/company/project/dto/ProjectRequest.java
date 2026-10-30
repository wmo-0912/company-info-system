package com.example.company.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProjectRequest {

    @NotBlank(message = "项目编码不能为空")
    @Size(max = 64)
    private String projectCode;

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 150)
    private String projectName;

    @NotNull(message = "项目负责人不能为空")
    private Long managerEmployeeId;

    @Size(max = 500)
    private String description;
}
