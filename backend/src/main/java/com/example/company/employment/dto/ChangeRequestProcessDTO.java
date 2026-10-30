package com.example.company.employment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangeRequestProcessDTO {

    @NotBlank(message = "处理决定不能为空")
    private String decision; // APPROVE / REJECT

    @Size(max = 1000)
    private String comment;
}
