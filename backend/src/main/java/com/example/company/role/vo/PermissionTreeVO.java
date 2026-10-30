package com.example.company.role.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PermissionTreeVO {

    private Long id;
    private String permissionCode;
    private String permissionName;
    private String permissionType;
    private Long parentId;
    private String routePath;
    private String icon;
    private Integer sortOrder;
    private List<PermissionTreeVO> children = new ArrayList<>();
}
