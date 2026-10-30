package com.example.company.role.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 角色在某个模块上的数据范围覆盖，未配置时沿用 sys_role.data_scope */
@Data
@TableName("sys_role_module_scope")
public class SysRoleModuleScope {

    private Long roleId;

    private String moduleCode;

    private String scopeType;
}
