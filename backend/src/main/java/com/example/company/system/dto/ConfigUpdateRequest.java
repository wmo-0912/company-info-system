package com.example.company.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ConfigUpdateRequest {

    @NotBlank(message = "配置键不能为空")
    @Size(max = 100)
    private String configKey;

    @Size(max = 2000)
    private String configValue;

    @Size(max = 500)
    private String description;
}
