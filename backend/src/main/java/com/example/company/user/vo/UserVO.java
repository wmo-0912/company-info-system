package com.example.company.user.vo;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
public class UserVO {

    private Long id;
    private String username;
    private Long employeeId;
    private String employeeName;
    private String status;
    private OffsetDateTime lastLoginAt;
    private List<String> roles;
    private List<Long> roleIds;
}
