package com.example.company.common.enums;

/**
 * 可按模块单独配置数据范围的业务模块。
 * 角色级 data_scope 是默认值，模块级配置覆盖它（见 sys_role_module_scope）。
 */
public enum DataModule {
    /** 员工档案：员工列表、档案详情、部门员工列表 */
    EMPLOYEE,
    /** 组织架构：部门树与管理 */
    DEPARTMENT,
    /** 项目组：项目组列表、详情、成员 */
    PROJECT,
    /** 绩效考核：评价列表与详情 */
    PERFORMANCE,
    /** 入转调离：申请列表与审批 */
    EMPLOYMENT
}
