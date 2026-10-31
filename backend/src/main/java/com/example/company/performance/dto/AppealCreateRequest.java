package com.example.company.performance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AppealCreateRequest {

    @NotBlank(message = "申诉理由不能为空")
    @Size(max = 500, message = "申诉理由不能超过 500 个字符")
    private String reason;
}
