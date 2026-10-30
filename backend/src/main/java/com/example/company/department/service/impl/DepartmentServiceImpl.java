package com.example.company.department.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.company.audit.service.AuditService;
import com.example.company.common.api.PageResponse;
import com.example.company.common.enums.EmployeeStatus;
import com.example.company.common.exception.BusinessException;
import com.example.company.common.security.DataScopeService;
import com.example.company.common.security.SecurityUtils;
import com.example.company.department.dto.DepartmentRequest;
import com.example.company.department.entity.Department;
import com.example.company.department.mapper.DepartmentMapper;
import com.example.company.department.service.DepartmentService;
import com.example.company.department.vo.DepartmentVO;
import com.example.company.employee.entity.Employee;
import com.example.company.employee.mapper.EmployeeMapper;
import com.example.company.employee.service.EmployeeConverter;
import com.example.company.employee.vo.EmployeeListItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentMapper departmentMapper;
    private final EmployeeMapper employeeMapper;
    private final DataScopeService dataScopeService;
    private final AuditService auditService;

    @Override
    public List<DepartmentVO> tree() {
        List<Department> all = departmentMapper.selectList(new QueryWrapper<Department>().orderByAsc("department_code"));
        Map<Long, DepartmentVO> nodes = all.stream()
                .map(this::toVO)
                .collect(Collectors.toMap(DepartmentVO::getId, Function.identity()));
        List<DepartmentVO> roots = new ArrayList<>();
        for (DepartmentVO node : nodes.values()) {
            if (node.getParentId() != null && nodes.containsKey(node.getParentId())) {
                nodes.get(node.getParentId()).getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    @Override
    public PageResponse<DepartmentVO> page(int page, int pageSize, String keyword, String status) {
        QueryWrapper<Department> qw = new QueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like("department_name", keyword).or().like("department_code", keyword));
        }
        if (StringUtils.hasText(status)) {
            qw.eq("status", status);
        }
        qw.orderByAsc("department_code");
        Page<Department> result = departmentMapper.selectPage(new Page<>(page, pageSize), qw);
        return PageResponse.from(result, this::toVO);
    }

    @Override
    public DepartmentVO detail(Long id) {
        return toVO(requireDepartment(id));
    }

    @Override
    @Transactional
    public DepartmentVO create(DepartmentRequest request) {
        assertCodeUnique(request.getDepartmentCode(), null);
        Department dept = new Department();
        apply(dept, request);
        dept.setStatus("ACTIVE");
        departmentMapper.insert(dept);
        auditService.success("department:create", "department", dept.getId(), "创建部门 " + dept.getDepartmentName());
        return toVO(dept);
    }

    @Override
    @Transactional
    public DepartmentVO update(Long id, DepartmentRequest request) {
        Department dept = requireDepartment(id);
        assertCodeUnique(request.getDepartmentCode(), id);
        if (request.getParentId() != null && request.getParentId().equals(id)) {
            throw BusinessException.unprocessable("父部门不能是自身");
        }
        apply(dept, request);
        // 防循环：从父部门向上回溯，若链路中出现自身则构成环
        if (wouldCreateCycle(id, dept.getParentId())) {
            throw BusinessException.unprocessable("部门调整会形成循环父子关系");
        }
        departmentMapper.updateById(dept);
        auditService.success("department:update", "department", id, "更新部门 " + dept.getDepartmentName());
        return toVO(dept);
    }

    @Override
    @Transactional
    public void changeStatus(Long id, String status) {
        if (!"ACTIVE".equals(status) && !"DISABLED".equals(status)) {
            throw BusinessException.badRequest("非法的部门状态");
        }
        Department dept = requireDepartment(id);
        if ("DISABLED".equals(status)) {
            Long activeChildren = departmentMapper.selectCount(new QueryWrapper<Department>()
                    .eq("parent_id", id).eq("status", "ACTIVE"));
            if (activeChildren > 0) {
                throw BusinessException.conflict("存在有效子部门，不能停用");
            }
            Long activeEmployees = employeeMapper.selectCount(new QueryWrapper<Employee>()
                    .eq("department_id", id)
                    .ne("employment_status", EmployeeStatus.RESIGNED.name()));
            if (activeEmployees > 0) {
                throw BusinessException.conflict("部门下仍有在职员工，不能停用");
            }
        }
        dept.setStatus(status);
        departmentMapper.updateById(dept);
        auditService.success("department:status", "department", id, "部门状态调整为 " + status);
    }

    @Override
    public PageResponse<EmployeeListItemVO> employees(Long id, int page, int pageSize, String status) {
        requireDepartment(id);
        // 部门维度列表同样受数据权限约束，否则可绕过员工列表按部门 ID 直查全员
        Set<Long> visible = dataScopeService.visibleEmployeeIds(SecurityUtils.getCurrentUser());
        if (visible != null && visible.isEmpty()) {
            return PageResponse.of(List.of(), page, pageSize, 0);
        }
        QueryWrapper<Employee> qw = new QueryWrapper<Employee>().eq("department_id", id);
        if (visible != null) {
            qw.in("id", visible);
        }
        if (org.springframework.util.StringUtils.hasText(status)) {
            List<String> statuses = java.util.Arrays.stream(status.split(","))
                    .map(String::trim).filter(org.springframework.util.StringUtils::hasText).toList();
            if (statuses.size() == 1) {
                qw.eq("employment_status", statuses.get(0));
            } else if (!statuses.isEmpty()) {
                qw.in("employment_status", statuses);
            }
        }
        qw.orderByAsc("employee_no");
        Page<Employee> result = employeeMapper.selectPage(new Page<>(page, pageSize), qw);
        return PageResponse.from(result, EmployeeConverter::toListItem);
    }

    private Department requireDepartment(Long id) {
        Department dept = departmentMapper.selectById(id);
        if (dept == null) {
            throw BusinessException.notFound("部门不存在");
        }
        return dept;
    }

    private void assertCodeUnique(String code, Long excludeId) {
        QueryWrapper<Department> qw = new QueryWrapper<Department>().eq("department_code", code);
        if (excludeId != null) {
            qw.ne("id", excludeId);
        }
        if (departmentMapper.selectCount(qw) > 0) {
            throw BusinessException.conflict("部门编码已存在: " + code);
        }
    }

    private void apply(Department dept, DepartmentRequest request) {
        if (request.getParentId() != null) {
            requireDepartment(request.getParentId());
        }
        if (request.getManagerEmployeeId() != null) {
            Employee manager = employeeMapper.selectById(request.getManagerEmployeeId());
            if (manager == null) {
                throw BusinessException.badRequest("部门负责人不存在");
            }
        }
        dept.setDepartmentCode(request.getDepartmentCode());
        dept.setDepartmentName(request.getDepartmentName());
        dept.setParentId(request.getParentId());
        dept.setManagerEmployeeId(request.getManagerEmployeeId());
        dept.setDescription(request.getDescription());
    }

    private boolean wouldCreateCycle(Long id, Long newParentId) {
        Set<Long> visited = new HashSet<>();
        Long cursor = newParentId;
        while (cursor != null) {
            if (cursor.equals(id)) {
                return true;
            }
            if (!visited.add(cursor)) {
                return true; // 已存在环，拒绝
            }
            Department parent = departmentMapper.selectById(cursor);
            cursor = parent == null ? null : parent.getParentId();
        }
        return false;
    }

    private DepartmentVO toVO(Department dept) {
        DepartmentVO vo = new DepartmentVO();
        vo.setId(dept.getId());
        vo.setDepartmentCode(dept.getDepartmentCode());
        vo.setDepartmentName(dept.getDepartmentName());
        vo.setParentId(dept.getParentId());
        vo.setManagerEmployeeId(dept.getManagerEmployeeId());
        if (dept.getManagerEmployeeId() != null) {
            Employee manager = employeeMapper.selectById(dept.getManagerEmployeeId());
            vo.setManagerEmployeeName(manager == null ? null : manager.getEmployeeName());
        }
        vo.setDescription(dept.getDescription());
        vo.setStatus(dept.getStatus());
        vo.setChildren(new ArrayList<>());
        return vo;
    }
}
