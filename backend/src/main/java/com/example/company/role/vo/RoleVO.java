package com.example.company.role.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class RoleVO {

    private Long id;
    private String roleCode;
    private String roleName;
    private String description;
    private String dataScope;
    private Boolean enabled;
    private List<Long> permissionIds;

    /** 模块级数据范围覆盖：moduleCode -> scope，未配置的模块沿用 dataScope */
    private Map<String, String> moduleScopes;
}
