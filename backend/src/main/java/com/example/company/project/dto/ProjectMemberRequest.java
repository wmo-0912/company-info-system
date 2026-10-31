package com.example.company.project.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ProjectMemberRequest {

    @NotNull(message = "员工不能为空")
    private Long employeeId;

    @Size(max = 64)
    private String memberRole;

    /** 加入日期，补录历史项目时使用；留空取当天 */
    @PastOrPresent(message = "加入日期不能晚于今天")
    private LocalDate joinedAt;
}
