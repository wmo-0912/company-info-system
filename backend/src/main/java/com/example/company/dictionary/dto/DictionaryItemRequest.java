package com.example.company.dictionary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DictionaryItemRequest {

    @NotBlank(message = "字典项编码不能为空")
    @Size(max = 64)
    private String itemCode;

    @NotBlank(message = "字典项名称不能为空")
    @Size(max = 100)
    private String itemLabel;

    private Integer sortOrder;
}
