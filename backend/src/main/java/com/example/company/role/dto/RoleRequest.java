package com.example.company.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RoleRequest {

    @NotBlank(message = "角色编码不能为空")
    @Size(max = 64)
    private String roleCode;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 100)
    private String roleName;

    @Size(max = 500)
    private String description;

    @NotBlank(message = "数据范围不能为空")
    @Pattern(regexp = "ALL|DEPARTMENT|PROJECT|SELF", message = "数据范围必须是 ALL/DEPARTMENT/PROJECT/SELF")
    private String dataScope;
}
