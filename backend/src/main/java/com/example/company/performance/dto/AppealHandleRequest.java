package com.example.company.performance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AppealHandleRequest {

    @NotBlank(message = "处理结果不能为空")
    @Pattern(regexp = "ACCEPT|REJECT", message = "处理结果只能是 ACCEPT 或 REJECT")
    private String decision;

    @Size(max = 500, message = "处理意见不能超过 500 个字符")
    private String comment;
}
