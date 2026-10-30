package com.example.company.department.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.company.department.entity.Department;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DepartmentMapper extends BaseMapper<Department> {
}
