package com.example.company.user.vo;

import lombok.Data;

import java.util.Set;

@Data
public class CurrentUserVO {

    private Long id;
    private String username;
    private String displayName;
    private Long employeeId;
    private Set<String> roles;
    private Set<String> permissions;
    private String dataScope;
}
