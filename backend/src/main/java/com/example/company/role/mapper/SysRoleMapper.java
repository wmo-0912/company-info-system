package com.example.company.role.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.company.role.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    @Select("SELECT r.* FROM sys_role r JOIN sys_user_role ur ON ur.role_id = r.id " +
            "WHERE ur.user_id = #{userId} AND r.deleted = FALSE AND r.enabled = TRUE")
    List<SysRole> selectByUserId(@Param("userId") Long userId);
}
