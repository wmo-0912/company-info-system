package com.example.company.profile.controller;

import com.example.company.audit.service.AuditService;
import com.example.company.common.api.ApiResponse;
import com.example.company.common.exception.BusinessException;
import com.example.company.common.security.SecurityUtils;
import com.example.company.department.entity.Department;
import com.example.company.department.mapper.DepartmentMapper;
import com.example.company.employee.entity.Employee;
import com.example.company.employee.mapper.EmployeeMapper;
import com.example.company.employee.service.EmployeeConverter;
import com.example.company.employee.vo.EmployeeDetailVO;
import com.example.company.employment.service.EmploymentService;
import com.example.company.employment.vo.EmploymentRecordVO;
import com.example.company.profile.dto.ProfileUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "个人中心")
@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final EmployeeMapper employeeMapper;
    private final DepartmentMapper departmentMapper;
    private final EmploymentService employmentService;
    private final AuditService auditService;

    @Operation(summary = "我的档案")
    @GetMapping
    @PreAuthorize("hasAuthority('profile:read')")
    public ApiResponse<EmployeeDetailVO> myProfile() {
        Employee employee = requireSelf();
        Department dept = departmentMapper.selectById(employee.getDepartmentId());
        Employee manager = employee.getManagerEmployeeId() == null ? null
                : employeeMapper.selectById(employee.getManagerEmployeeId());
        return ApiResponse.ok(EmployeeConverter.toDetail(employee, dept, manager, true));
    }

    @Operation(summary = "更新我的资料（白名单字段）")
    @PutMapping
    @PreAuthorize("hasAuthority('profile:update')")
    @Transactional
    public ApiResponse<EmployeeDetailVO> updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        Employee employee = requireSelf();
        if ("RESIGNED".equals(employee.getEmploymentStatus())) {
            throw BusinessException.unprocessable("已离职员工档案不可编辑");
        }
        employee.setPhone(request.getPhone());
        employee.setEmail(request.getEmail());
        employee.setAddress(request.getAddress());
        employee.setEmergencyContactName(request.getEmergencyContactName());
        employee.setEmergencyContactPhone(request.getEmergencyContactPhone());
        employee.setEducationCode(request.getEducationCode());
        employeeMapper.updateById(employee);
        auditService.success("profile:update", "employee", employee.getId(), "个人资料更新（白名单字段）");
        return myProfile();
    }

    @Operation(summary = "我的任职历史")
    @GetMapping("/employment-records")
    @PreAuthorize("hasAuthority('profile:read')")
    public ApiResponse<List<EmploymentRecordVO>> myRecords() {
        return ApiResponse.ok(employmentService.recordsOfEmployee(requireSelf().getId()));
    }

    private Employee requireSelf() {
        Long employeeId = SecurityUtils.getCurrentEmployeeId();
        if (employeeId == null) {
            throw BusinessException.notFound("当前账号未关联员工档案");
        }
        Employee employee = employeeMapper.selectById(employeeId);
        if (employee == null) {
            throw BusinessException.notFound("员工档案不存在");
        }
        return employee;
    }
}
