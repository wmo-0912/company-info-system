package com.example.company.performance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.company.common.enums.DataModule;
import com.example.company.audit.service.AuditService;
import com.example.company.common.api.PageResponse;
import com.example.company.common.enums.PeriodType;
import com.example.company.common.enums.ReviewStatus;
import com.example.company.common.exception.BusinessException;
import com.example.company.common.security.DataScopeService;
import com.example.company.common.security.LoginUser;
import com.example.company.common.security.SecurityUtils;
import com.example.company.department.entity.Department;
import com.example.company.department.mapper.DepartmentMapper;
import com.example.company.employee.entity.Employee;
import com.example.company.employee.mapper.EmployeeMapper;
import com.example.company.performance.dto.PerformanceReviewRequest;
import com.example.company.performance.entity.PerformanceReview;
import com.example.company.performance.mapper.PerformanceReviewMapper;
import com.example.company.performance.service.PerformancePeriodService;
import com.example.company.performance.service.PerformanceService;
import com.example.company.performance.vo.PerformanceReviewVO;
import com.example.company.performance.vo.PerformanceSummaryVO;
import com.example.company.project.entity.ProjectTeam;
import com.example.company.project.entity.ProjectTeamMember;
import com.example.company.project.mapper.ProjectTeamMapper;
import com.example.company.project.mapper.ProjectTeamMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PerformanceServiceImpl implements PerformanceService {

    private final PerformanceReviewMapper reviewMapper;
    private final EmployeeMapper employeeMapper;
    private final ProjectTeamMapper teamMapper;
    private final ProjectTeamMemberMapper memberMapper;
    private final AuditService auditService;
    private final DataScopeService dataScopeService;
    private final PerformancePeriodService periodService;
    private final DepartmentMapper departmentMapper;

    @Override
    public PageResponse<PerformanceReviewVO> page(int page, int pageSize, String periodType, String periodKey, String status) {
        LoginUser current = SecurityUtils.getCurrentUser();
        QueryWrapper<PerformanceReview> qw = new QueryWrapper<>();
        if (!applyScope(qw, current)) {
            return PageResponse.of(List.of(), page, pageSize, 0);
        }
        if (StringUtils.hasText(periodType)) {
            qw.eq("period_type", periodType);
        }
        if (StringUtils.hasText(periodKey)) {
            qw.eq("period_key", periodKey);
        }
        if (StringUtils.hasText(status)) {
            qw.eq("status", status);
        }
        qw.orderByDesc("created_at");
        Page<PerformanceReview> result = reviewMapper.selectPage(new Page<>(page, pageSize), qw);
        return PageResponse.of(toVOs(result.getRecords()), result.getCurrent(), result.getSize(), result.getTotal());
    }

    /**
     * 按统一数据范围过滤：能看“我评价的记录”与“数据范围内的人被评价的非草稿记录”。
     *
     * @return false 表示当前用户没有任何可见员工，直接返回空页
     */
    private boolean applyScope(QueryWrapper<PerformanceReview> qw, LoginUser current) {
        Set<Long> visible = dataScopeService.visibleEmployeeIds(current, DataModule.PERFORMANCE);
        if (visible == null) {
            return true; // ALL：全部可见
        }
        if (visible.isEmpty()) {
            return false;
        }
        Long me = current.getEmployeeId();
        String draft = ReviewStatus.DRAFT.name();
        if (me == null) {
            qw.in("employee_id", visible).ne("status", draft);
            return true;
        }
        qw.and(w -> w.eq("reviewer_employee_id", me)
                .or(x -> x.in("employee_id", visible).ne("status", draft)));
        return true;
    }

    @Override
    public PerformanceReviewVO detail(Long id) {
        PerformanceReview review = requireReview(id);
        LoginUser current = SecurityUtils.getCurrentUser();
        Set<Long> visible = dataScopeService.visibleEmployeeIds(current, DataModule.PERFORMANCE);
        if (visible == null) {
            return toVO(review); // 全公司范围（管理员/人事）
        }
        Long me = current.getEmployeeId();
        boolean isReviewer = me != null && me.equals(review.getReviewerEmployeeId());
        boolean inScope = visible.contains(review.getEmployeeId());
        if (!isReviewer && !inScope) {
            throw BusinessException.forbidden("无权查看该绩效记录");
        }
        // 被评价人本人看不到还在草稿阶段的评价
        if (!isReviewer && ReviewStatus.DRAFT.name().equals(review.getStatus())) {
            throw BusinessException.forbidden("草稿状态的评价对被评价人不可见");
        }
        return toVO(review);
    }

    @Override
    @Transactional
    public PerformanceReviewVO create(PerformanceReviewRequest request) {
        LoginUser current = SecurityUtils.getCurrentUser();
        Long reviewerEmployeeId = current.getEmployeeId();
        if (reviewerEmployeeId == null) {
            throw BusinessException.forbidden("当前账号未关联员工档案，无法评价");
        }
        validateReviewable(request, reviewerEmployeeId);
        assertPeriodOpen(request.getPeriodKey());

        // 唯一约束（employee, reviewer, period_type, period_key）
        Long exists = reviewMapper.selectCount(new QueryWrapper<PerformanceReview>()
                .eq("employee_id", request.getEmployeeId())
                .eq("reviewer_employee_id", reviewerEmployeeId)
                .eq("period_type", request.getPeriodType())
                .eq("period_key", request.getPeriodKey()));
        if (exists > 0) {
            throw BusinessException.conflict("该员工此周期已存在您的评价记录");
        }

        PerformanceReview review = new PerformanceReview();
        review.setEmployeeId(request.getEmployeeId());
        review.setReviewerEmployeeId(reviewerEmployeeId); // 评价人始终来自当前登录用户，不信任客户端
        review.setProjectTeamId(request.getProjectTeamId());
        review.setPeriodType(request.getPeriodType());
        review.setPeriodKey(request.getPeriodKey());
        review.setScore(request.getScore());
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        review.setStatus(ReviewStatus.DRAFT.name());
        try {
            reviewMapper.insert(review);
        } catch (DuplicateKeyException e) {
            // 并发提交时唯一约束兜底，避免直接冒 500
            throw BusinessException.conflict("该员工此周期已存在您的评价记录");
        }
        auditService.success("performance:create", "performance_review", review.getId(),
                "创建绩效评价 周期=" + request.getPeriodKey());
        return toVO(review);
    }

    @Override
    @Transactional
    public PerformanceReviewVO update(Long id, PerformanceReviewRequest request) {
        PerformanceReview review = requireReview(id);
        assertOwner(review);
        if (!ReviewStatus.DRAFT.name().equals(review.getStatus())
                && !ReviewStatus.RETURNED.name().equals(review.getStatus())) {
            throw BusinessException.unprocessable("已提交的评价不可修改，如需变更请联系人事退回");
        }
        validateReviewable(request, review.getReviewerEmployeeId());
        // 改周期时要同时检查旧周期与新周期：锁定的周期既不能被改内容，也不能被改出去
        assertPeriodOpen(review.getPeriodKey());
        assertPeriodOpen(request.getPeriodKey());
        review.setProjectTeamId(request.getProjectTeamId());
        review.setPeriodType(request.getPeriodType());
        review.setPeriodKey(request.getPeriodKey());
        review.setScore(request.getScore());
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        if (ReviewStatus.RETURNED.name().equals(review.getStatus())) {
            review.setStatus(ReviewStatus.DRAFT.name());
        }
        try {
            reviewMapper.updateById(review);
        } catch (DuplicateKeyException e) {
            throw BusinessException.conflict("该员工此周期已存在您的评价记录");
        }
        auditService.success("performance:update", "performance_review", id,
                "更新绩效评价 周期=" + review.getPeriodKey() + " 状态=" + review.getStatus());
        return toVO(review);
    }

    @Override
    @Transactional
    public PerformanceReviewVO submit(Long id) {
        PerformanceReview review = requireReview(id);
        assertOwner(review);
        if (!ReviewStatus.DRAFT.name().equals(review.getStatus())) {
            throw BusinessException.unprocessable("仅草稿状态的评价可以提交");
        }
        if (review.getScore() == null) {
            throw BusinessException.unprocessable("提交前必须填写评分");
        }
        assertPeriodOpen(review.getPeriodKey());
        review.setStatus(ReviewStatus.SUBMITTED.name());
        review.setSubmittedAt(OffsetDateTime.now());
        reviewMapper.updateById(review);
        auditService.success("performance:submit", "performance_review", id, "提交绩效评价");
        return toVO(review);
    }

    @Override
    @Transactional
    public PerformanceReviewVO returnForRevision(Long id, String reason) {
        PerformanceReview review = requireReview(id);
        LoginUser current = SecurityUtils.getCurrentUser();
        if (review.getReviewerEmployeeId().equals(current.getEmployeeId())) {
            throw BusinessException.badRequest("不能退回自己提交的评价");
        }
        if (!ReviewStatus.SUBMITTED.name().equals(review.getStatus())) {
            throw BusinessException.unprocessable("仅已提交的评价可以退回");
        }
        review.setStatus(ReviewStatus.RETURNED.name());
        reviewMapper.updateById(review);
        auditService.success("performance:return", "performance_review", id,
                "退回绩效评价：" + (StringUtils.hasText(reason) ? reason : "未填写原因")
                        + "（周期 " + review.getPeriodKey() + "）");
        return toVO(review);
    }

    @Override
    public PageResponse<PerformanceReviewVO> my(int page, int pageSize) {
        LoginUser current = SecurityUtils.getCurrentUser();
        if (current.getEmployeeId() == null) {
            return PageResponse.of(List.of(), page, pageSize, 0);
        }
        Page<PerformanceReview> result = reviewMapper.selectPage(new Page<>(page, pageSize),
                new QueryWrapper<PerformanceReview>()
                        .eq("employee_id", current.getEmployeeId())
                        .in("status", ReviewStatus.SUBMITTED.name(), ReviewStatus.RETURNED.name())
                        .orderByDesc("created_at"));
        return PageResponse.of(toVOs(result.getRecords()), result.getCurrent(), result.getSize(), result.getTotal());
    }


    @Override
    public PerformanceSummaryVO summary(String periodType, String periodKey) {
        LoginUser current = SecurityUtils.getCurrentUser();
        PerformanceSummaryVO vo = new PerformanceSummaryVO();
        vo.setPeriodType(periodType);
        vo.setPeriodKey(periodKey);

        Set<Long> visible = dataScopeService.visibleEmployeeIds(current, DataModule.PERFORMANCE);
        if (visible != null && visible.isEmpty()) {
            return emptySummary(vo); // 数据范围内没有任何员工，直接返回全零
        }

        QueryWrapper<PerformanceReview> qw = new QueryWrapper<PerformanceReview>()
                .eq("status", ReviewStatus.SUBMITTED.name()); // 只统计已提交，草稿与被退回的都不算
        if (StringUtils.hasText(periodType)) {
            qw.eq("period_type", periodType.trim());
        }
        if (StringUtils.hasText(periodKey)) {
            qw.eq("period_key", periodKey.trim());
        }
        if (visible != null) {
            qw.in("employee_id", visible);
        }
        List<PerformanceReview> reviews = reviewMapper.selectList(qw);
        if (reviews == null || reviews.isEmpty()) {
            return emptySummary(vo);
        }

        // 单周期、全公司不超过 200 人，明细规模有限，直接在内存聚合，避免复杂的多表 group by
        Map<Long, Employee> employees = new LinkedHashMap<>();
        List<Long> employeeIds = reviews.stream().map(PerformanceReview::getEmployeeId)
                .filter(Objects::nonNull).distinct().toList();
        if (!employeeIds.isEmpty()) {
            List<Employee> found = employeeMapper.selectBatchIds(employeeIds);
            if (found != null) {
                found.forEach(e -> employees.put(e.getId(), e));
            }
        }

        Set<Long> departmentIds = new LinkedHashSet<>();
        employees.values().stream().map(Employee::getDepartmentId).filter(Objects::nonNull)
                .forEach(departmentIds::add);
        Map<Long, Department> departments = new LinkedHashMap<>();
        if (!departmentIds.isEmpty()) {
            List<Department> found = departmentMapper.selectBatchIds(departmentIds);
            if (found != null) {
                found.forEach(d -> departments.put(d.getId(), d));
            }
        }

        Set<Long> teamIds = reviews.stream().map(PerformanceReview::getProjectTeamId)
                .filter(Objects::nonNull).distinct().collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<Long, ProjectTeam> teams = new LinkedHashMap<>();
        if (!teamIds.isEmpty()) {
            List<ProjectTeam> found = teamMapper.selectBatchIds(teamIds);
            if (found != null) {
                found.forEach(t -> teams.put(t.getId(), t));
            }
        }

        long[] bucketCounts = new long[5];
        Map<Long, GroupAcc> byDepartment = new LinkedHashMap<>();
        Map<Long, GroupAcc> byProject = new LinkedHashMap<>();
        Set<Long> scoredEmployees = new LinkedHashSet<>();
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal min = null;
        BigDecimal max = null;
        int scored = 0;

        for (PerformanceReview review : reviews) {
            if (review.getEmployeeId() != null) {
                scoredEmployees.add(review.getEmployeeId());
            }
            Employee employee = review.getEmployeeId() == null ? null : employees.get(review.getEmployeeId());
            Long departmentId = employee == null ? null : employee.getDepartmentId();
            String departmentName = departmentId == null ? "未设置部门"
                    : (departments.get(departmentId) == null ? "未知部门" : departments.get(departmentId).getDepartmentName());
            byDepartment.computeIfAbsent(departmentId, k -> new GroupAcc(departmentId, departmentName))
                    .add(review.getEmployeeId(), review.getScore());

            Long teamId = review.getProjectTeamId();
            String teamName = teamId == null ? "未关联项目组"
                    : (teams.get(teamId) == null ? "已删除项目组" : teams.get(teamId).getProjectName());
            byProject.computeIfAbsent(teamId, k -> new GroupAcc(teamId, teamName))
                    .add(review.getEmployeeId(), review.getScore());

            BigDecimal score = review.getScore();
            if (score == null) {
                continue; // 未打分的记录只计入条数，不参与分数统计
            }
            scored++;
            sum = sum.add(score);
            min = min == null || score.compareTo(min) < 0 ? score : min;
            max = max == null || score.compareTo(max) > 0 ? score : max;
            bucketCounts[bucketIndex(score)]++;
        }

        vo.setReviewCount(reviews.size());
        vo.setEmployeeCount(scoredEmployees.size());
        vo.setMinScore(min);
        vo.setMaxScore(max);
        vo.setAverageScore(scored == 0 ? null : sum.divide(BigDecimal.valueOf(scored), 2, RoundingMode.HALF_UP));
        vo.setScoreBuckets(List.of(
                new PerformanceSummaryVO.BucketVO(BUCKET_LABELS[0], bucketCounts[0]),
                new PerformanceSummaryVO.BucketVO(BUCKET_LABELS[1], bucketCounts[1]),
                new PerformanceSummaryVO.BucketVO(BUCKET_LABELS[2], bucketCounts[2]),
                new PerformanceSummaryVO.BucketVO(BUCKET_LABELS[3], bucketCounts[3]),
                new PerformanceSummaryVO.BucketVO(BUCKET_LABELS[4], bucketCounts[4])));
        vo.setDepartments(byDepartment.values().stream().map(GroupAcc::toVO).toList());
        vo.setProjects(byProject.values().stream().map(GroupAcc::toVO).toList());
        return vo;
    }

    private static final String[] BUCKET_LABELS = {"90-100", "80-89", "70-79", "60-69", "60 以下"};

    private static int bucketIndex(BigDecimal score) {
        if (score.compareTo(BigDecimal.valueOf(90)) >= 0) {
            return 0;
        }
        if (score.compareTo(BigDecimal.valueOf(80)) >= 0) {
            return 1;
        }
        if (score.compareTo(BigDecimal.valueOf(70)) >= 0) {
            return 2;
        }
        if (score.compareTo(BigDecimal.valueOf(60)) >= 0) {
            return 3;
        }
        return 4;
    }

    private PerformanceSummaryVO emptySummary(PerformanceSummaryVO vo) {
        vo.setReviewCount(0);
        vo.setEmployeeCount(0);
        List<PerformanceSummaryVO.BucketVO> buckets = new ArrayList<>();
        for (String label : BUCKET_LABELS) {
            buckets.add(new PerformanceSummaryVO.BucketVO(label, 0));
        }
        vo.setScoreBuckets(buckets);
        return vo;
    }

    /** 分组累加器：同时记录评价条数、去重后的人数和分数合计 */
    private static final class GroupAcc {
        private final Long id;
        private final String name;
        private final Set<Long> employees = new LinkedHashSet<>();
        private long reviewCount;
        private BigDecimal sum = BigDecimal.ZERO;
        private int scored;

        private GroupAcc(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        private void add(Long employeeId, BigDecimal score) {
            reviewCount++;
            if (employeeId != null) {
                employees.add(employeeId);
            }
            if (score != null) {
                scored++;
                sum = sum.add(score);
            }
        }

        private PerformanceSummaryVO.GroupVO toVO() {
            PerformanceSummaryVO.GroupVO g = new PerformanceSummaryVO.GroupVO();
            g.setId(id);
            g.setName(name);
            g.setReviewCount(reviewCount);
            g.setEmployeeCount(employees.size());
            g.setAverageScore(scored == 0 ? null : sum.divide(BigDecimal.valueOf(scored), 2, RoundingMode.HALF_UP));
            return g;
        }
    }

    /** 周期锁定后不允许新增/修改/提交，避免汇总完成后记录又被改回去 */
    private void assertPeriodOpen(String periodKey) {
        if (periodService.isLocked(periodKey)) {
            throw BusinessException.unprocessable("绩效周期 " + periodKey + " 已锁定，不能再新增或修改评价");
        }
    }

    /** 校验被评价人是当前用户负责的项目组的有效成员（管理员/人事拥有全局权限可跳过项目组校验） */
    private void validateReviewable(PerformanceReviewRequest request, Long reviewerEmployeeId) {
        LoginUser current = SecurityUtils.getCurrentUser();
        if (request.getEmployeeId().equals(reviewerEmployeeId)) {
            throw BusinessException.badRequest("不能评价自己");
        }
        Employee target = employeeMapper.selectById(request.getEmployeeId());
        if (target == null) {
            throw BusinessException.badRequest("被评价员工不存在");
        }
        if (current.hasPermission("performance:create")
                && ("ALL".equals(dataScopeService.effectiveScope(current, DataModule.PERFORMANCE))
                || "DEPARTMENT".equals(dataScopeService.effectiveScope(current, DataModule.PERFORMANCE)))) {
            return; // 人事/管理员按授权评价
        }
        // 项目经理：被评价人必须是其负责项目组的有效成员
        List<ProjectTeam> teams = teamMapper.selectList(new QueryWrapper<ProjectTeam>()
                .eq("manager_employee_id", reviewerEmployeeId).eq("status", "ACTIVE"));
        if (teams.isEmpty()) {
            throw BusinessException.forbidden("您没有负责中的项目组，无法评价");
        }
        List<Long> teamIds = teams.stream().map(ProjectTeam::getId).toList();
        Long count = memberMapper.selectCount(new QueryWrapper<ProjectTeamMember>()
                .in("project_team_id", teamIds)
                .eq("employee_id", request.getEmployeeId())
                .isNull("left_at"));
        if (count == 0) {
            throw BusinessException.forbidden("被评价员工不在您负责的项目组内");
        }
        if (request.getProjectTeamId() != null && !teamIds.contains(request.getProjectTeamId())) {
            throw BusinessException.forbidden("只能关联您负责的项目组");
        }
    }

    private void assertOwner(PerformanceReview review) {
        LoginUser current = SecurityUtils.getCurrentUser();
        boolean isOwner = review.getReviewerEmployeeId().equals(current.getEmployeeId());
        boolean isAdmin = current.hasPermission("performance:update")
                && ("ALL".equals(dataScopeService.effectiveScope(current, DataModule.PERFORMANCE)));
        if (!isOwner && !isAdmin) {
            throw BusinessException.forbidden("只能操作本人创建的评价");
        }
    }

    private PerformanceReview requireReview(Long id) {
        PerformanceReview review = reviewMapper.selectById(id);
        if (review == null) {
            throw BusinessException.notFound("绩效记录不存在");
        }
        return review;
    }

    /** 批量组装：先收集员工/项目组 id 一次性查回，避免逐条 selectById（列表页 N+1） */
    private List<PerformanceReviewVO> toVOs(List<PerformanceReview> reviews) {
        if (reviews.isEmpty()) {
            return List.of();
        }
        Set<Long> employeeIds = new java.util.LinkedHashSet<>();
        Set<Long> teamIds = new java.util.LinkedHashSet<>();
        for (PerformanceReview r : reviews) {
            if (r.getEmployeeId() != null) {
                employeeIds.add(r.getEmployeeId());
            }
            if (r.getReviewerEmployeeId() != null) {
                employeeIds.add(r.getReviewerEmployeeId());
            }
            if (r.getProjectTeamId() != null) {
                teamIds.add(r.getProjectTeamId());
            }
        }
        Map<Long, Employee> employees = employeeIds.isEmpty() ? Map.of()
                : employeeMapper.selectBatchIds(employeeIds).stream()
                        .collect(Collectors.toMap(Employee::getId, Function.identity()));
        Map<Long, ProjectTeam> teams = teamIds.isEmpty() ? Map.of()
                : teamMapper.selectBatchIds(teamIds).stream()
                        .collect(Collectors.toMap(ProjectTeam::getId, Function.identity()));
        List<PerformanceReviewVO> list = new ArrayList<>(reviews.size());
        for (PerformanceReview r : reviews) {
            list.add(buildVO(r, employees.get(r.getEmployeeId()),
                    employees.get(r.getReviewerEmployeeId()),
                    r.getProjectTeamId() == null ? null : teams.get(r.getProjectTeamId())));
        }
        return list;
    }

    private PerformanceReviewVO toVO(PerformanceReview r) {
        Employee emp = r.getEmployeeId() == null ? null : employeeMapper.selectById(r.getEmployeeId());
        Employee reviewer = r.getReviewerEmployeeId() == null ? null : employeeMapper.selectById(r.getReviewerEmployeeId());
        ProjectTeam team = r.getProjectTeamId() == null ? null : teamMapper.selectById(r.getProjectTeamId());
        return buildVO(r, emp, reviewer, team);
    }

    private PerformanceReviewVO buildVO(PerformanceReview r, Employee emp, Employee reviewer, ProjectTeam team) {
        PerformanceReviewVO vo = new PerformanceReviewVO();
        vo.setId(r.getId());
        vo.setEmployeeId(r.getEmployeeId());
        vo.setReviewerEmployeeId(r.getReviewerEmployeeId());
        vo.setProjectTeamId(r.getProjectTeamId());
        vo.setPeriodType(r.getPeriodType());
        vo.setPeriodKey(r.getPeriodKey());
        vo.setScore(r.getScore());
        vo.setRating(r.getRating());
        vo.setComment(r.getComment());
        vo.setStatus(r.getStatus());
        vo.setSubmittedAt(r.getSubmittedAt());
        vo.setCreatedAt(r.getCreatedAt());
        if (emp != null) {
            vo.setEmployeeName(emp.getEmployeeName());
            vo.setEmployeeNo(emp.getEmployeeNo());
        }
        vo.setReviewerName(reviewer == null ? null : reviewer.getEmployeeName());
        vo.setProjectTeamName(team == null ? null : team.getProjectName());
        return vo;
    }
}
