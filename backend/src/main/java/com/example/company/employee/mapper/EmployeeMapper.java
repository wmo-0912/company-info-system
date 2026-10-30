package com.example.company.employee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.company.employee.entity.Employee;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EmployeeMapper extends BaseMapper<Employee> {
}
