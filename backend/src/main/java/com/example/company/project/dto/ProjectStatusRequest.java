package com.example.company.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ProjectStatusRequest {

    /** ACTIVE = 进行中，CLOSED = 已关闭（关闭后不能再新增成员，成员可正常办理离开） */
    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "ACTIVE|CLOSED", message = "状态只能是 ACTIVE 或 CLOSED")
    private String status;
}
