package com.example.company.role.vo;

import lombok.Data;

import java.util.List;

@Data
public class RoleVO {

    private Long id;
    private String roleCode;
    private String roleName;
    private String description;
    private String dataScope;
    private Boolean enabled;
    private List<Long> permissionIds;
}
