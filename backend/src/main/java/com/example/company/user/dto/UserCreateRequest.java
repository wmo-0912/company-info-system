package com.example.company.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UserCreateRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 64)
    private String username;

    @NotBlank(message = "初始密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度需为 8-64 位")
    private String password;

    private Long employeeId;

    @NotEmpty(message = "至少分配一个角色")
    private List<Long> roleIds;
}
