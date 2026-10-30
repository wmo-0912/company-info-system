package com.example.company.dictionary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DictionaryTypeRequest {

    @NotBlank(message = "字典编码不能为空")
    @Size(max = 64)
    private String dictCode;

    @NotBlank(message = "字典名称不能为空")
    @Size(max = 100)
    private String dictName;
}
