package com.example.company.employee.service;

import com.example.company.department.entity.Department;
import com.example.company.employee.entity.Employee;
import com.example.company.employee.vo.EmployeeDetailVO;
import com.example.company.employee.vo.EmployeeListItemVO;

/** 员工实体到视图对象的转换 */
public final class EmployeeConverter {

    private EmployeeConverter() {
    }

    public static EmployeeListItemVO toListItem(Employee e) {
        EmployeeListItemVO vo = new EmployeeListItemVO();
        vo.setId(e.getId());
        vo.setEmployeeNo(e.getEmployeeNo());
        vo.setEmployeeName(e.getEmployeeName());
        vo.setGender(e.getGender());
        vo.setDepartmentId(e.getDepartmentId());
        vo.setPositionName(e.getPositionName());
        vo.setPositionLevelCode(e.getPositionLevelCode());
        vo.setEmploymentStatus(e.getEmploymentStatus());
        vo.setHireDate(e.getHireDate());
        return vo;
    }

    public static EmployeeDetailVO toDetail(Employee e, Department dept, Employee manager, boolean includeSensitive) {
        EmployeeDetailVO vo = new EmployeeDetailVO();
        vo.setId(e.getId());
        vo.setEmployeeNo(e.getEmployeeNo());
        vo.setEmployeeName(e.getEmployeeName());
        vo.setGender(e.getGender());
        vo.setDepartmentId(e.getDepartmentId());
        vo.setDepartmentName(dept == null ? null : dept.getDepartmentName());
        vo.setPositionName(e.getPositionName());
        vo.setPositionLevelCode(e.getPositionLevelCode());
        vo.setManagerEmployeeId(e.getManagerEmployeeId());
        vo.setManagerEmployeeName(manager == null ? null : manager.getEmployeeName());
        vo.setHireDate(e.getHireDate());
        vo.setEmploymentStatus(e.getEmploymentStatus());
        vo.setResignationDate(e.getResignationDate());
        vo.setRemark(e.getRemark());
        if (includeSensitive) {
            vo.setBirthDate(e.getBirthDate());
            vo.setPhone(e.getPhone());
            vo.setEmail(e.getEmail());
            vo.setAddress(e.getAddress());
            vo.setEmergencyContactName(e.getEmergencyContactName());
            vo.setEmergencyContactPhone(e.getEmergencyContactPhone());
            vo.setEducationCode(e.getEducationCode());
        }
        return vo;
    }
}
