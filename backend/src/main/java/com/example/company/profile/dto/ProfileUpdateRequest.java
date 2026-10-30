package com.example.company.profile.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 个人中心可修改字段白名单：不含编号、部门、岗位、状态、日期等受控字段 */
@Data
public class ProfileUpdateRequest {

    @Size(max = 32)
    private String phone;

    @Email(message = "邮箱格式不正确")
    @Size(max = 120)
    private String email;

    @Size(max = 300)
    private String address;

    @Size(max = 100)
    private String emergencyContactName;

    @Size(max = 32)
    private String emergencyContactPhone;

    @Size(max = 64)
    private String educationCode;
}
