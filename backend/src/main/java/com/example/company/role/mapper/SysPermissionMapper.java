package com.example.company.role.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.company.role.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    @Select("SELECT DISTINCT p.* FROM sys_permission p " +
            "JOIN sys_role_permission rp ON rp.permission_id = p.id " +
            "JOIN sys_user_role ur ON ur.role_id = rp.role_id " +
            "JOIN sys_role r ON r.id = ur.role_id AND r.deleted = FALSE AND r.enabled = TRUE " +
            "WHERE ur.user_id = #{userId} AND p.enabled = TRUE")
    List<SysPermission> selectByUserId(@Param("userId") Long userId);

    @Select("SELECT p.* FROM sys_permission p " +
            "JOIN sys_role_permission rp ON rp.permission_id = p.id " +
            "WHERE rp.role_id = #{roleId} AND p.enabled = TRUE")
    List<SysPermission> selectByRoleId(@Param("roleId") Long roleId);
}
