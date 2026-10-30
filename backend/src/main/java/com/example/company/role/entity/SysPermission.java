package com.example.company.role.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_permission")
public class SysPermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String permissionCode;

    private String permissionName;

    private String permissionType;

    private Long parentId;

    private String routePath;

    private String icon;

    private Integer sortOrder;

    private Boolean enabled;
}
