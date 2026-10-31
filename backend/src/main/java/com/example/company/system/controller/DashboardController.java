package com.example.company.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.company.common.enums.DataModule;
import com.example.company.common.api.ApiResponse;
import com.example.company.common.security.DataScopeService;
import com.example.company.common.security.LoginUser;
import com.example.company.common.security.SecurityUtils;
import com.example.company.employee.entity.Employee;
import com.example.company.employee.mapper.EmployeeMapper;
import com.example.company.employment.entity.EmployeeChangeRequest;
import com.example.company.employment.mapper.EmployeeChangeRequestMapper;
import com.example.company.performance.entity.PerformanceReview;
import com.example.company.performance.mapper.PerformanceReviewMapper;
import com.example.company.project.entity.ProjectTeam;
import com.example.company.project.mapper.ProjectTeamMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Tag(name = "首页仪表盘")
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final EmployeeMapper employeeMapper;
    private final EmployeeChangeRequestMapper changeRequestMapper;
    private final ProjectTeamMapper teamMapper;
    private final PerformanceReviewMapper reviewMapper;
    private final DataScopeService dataScopeService;

    @Operation(summary = "统计摘要（按数据范围过滤）")
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public ApiResponse<Map<String, Object>> summary() {
        LoginUser current = SecurityUtils.getCurrentUser();
        Set<Long> visible = dataScopeService.visibleEmployeeIds(current, DataModule.EMPLOYEE);
        Map<String, Object> result = new HashMap<>();

        if (visible == null || !visible.isEmpty()) {
            QueryWrapper<Employee> empQuery = new QueryWrapper<>();
            if (visible != null) {
                empQuery.in("id", visible);
            }
            empQuery.ne("employment_status", "RESIGNED");
            result.put("employeeCount", employeeMapper.selectCount(empQuery));

            QueryWrapper<Employee> probationQuery = new QueryWrapper<Employee>().eq("employment_status", "PROBATION");
            if (visible != null) {
                probationQuery.in("id", visible);
            }
            result.put("probationCount", employeeMapper.selectCount(probationQuery));

            QueryWrapper<Employee> recentQuery = new QueryWrapper<Employee>()
                    .ge("hire_date", LocalDate.now().minusDays(30));
            if (visible != null) {
                recentQuery.in("id", visible);
            }
            result.put("recentHireCount", employeeMapper.selectCount(recentQuery));

            QueryWrapper<EmployeeChangeRequest> pendingQuery = new QueryWrapper<EmployeeChangeRequest>()
                    .eq("status", "SUBMITTED");
            if (visible != null) {
                pendingQuery.in("employee_id", visible);
            }
            result.put("pendingChangeCount", changeRequestMapper.selectCount(pendingQuery));
        } else {
            result.put("employeeCount", 0L);
            result.put("probationCount", 0L);
            result.put("recentHireCount", 0L);
            result.put("pendingChangeCount", 0L);
        }

        if (current.getEmployeeId() != null) {
            result.put("myTeamCount", teamMapper.selectCount(new QueryWrapper<ProjectTeam>()
                    .eq("manager_employee_id", current.getEmployeeId()).eq("status", "ACTIVE")));
            result.put("myDraftReviewCount", reviewMapper.selectCount(new QueryWrapper<PerformanceReview>()
                    .eq("reviewer_employee_id", current.getEmployeeId()).eq("status", "DRAFT")));
        } else {
            result.put("myTeamCount", 0L);
            result.put("myDraftReviewCount", 0L);
        }
        return ApiResponse.ok(result);
    }
}
