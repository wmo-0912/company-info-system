package com.example.company.performance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.company.audit.service.AuditService;
import com.example.company.common.api.PageResponse;
import com.example.company.common.enums.PeriodType;
import com.example.company.common.enums.ReviewStatus;
import com.example.company.common.exception.BusinessException;
import com.example.company.common.security.LoginUser;
import com.example.company.common.security.SecurityUtils;
import com.example.company.employee.entity.Employee;
import com.example.company.employee.mapper.EmployeeMapper;
import com.example.company.performance.dto.PerformanceReviewRequest;
import com.example.company.performance.entity.PerformanceReview;
import com.example.company.performance.mapper.PerformanceReviewMapper;
import com.example.company.performance.service.PerformanceService;
import com.example.company.performance.vo.PerformanceReviewVO;
import com.example.company.project.entity.ProjectTeam;
import com.example.company.project.entity.ProjectTeamMember;
import com.example.company.project.mapper.ProjectTeamMapper;
import com.example.company.project.mapper.ProjectTeamMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PerformanceServiceImpl implements PerformanceService {

    private final PerformanceReviewMapper reviewMapper;
    private final EmployeeMapper employeeMapper;
    private final ProjectTeamMapper teamMapper;
    private final ProjectTeamMemberMapper memberMapper;
    private final AuditService auditService;

    @Override
    public PageResponse<PerformanceReviewVO> page(int page, int pageSize, String periodType, String periodKey, String status) {
        LoginUser current = SecurityUtils.getCurrentUser();
        QueryWrapper<PerformanceReview> qw = new QueryWrapper<>();
        applyScope(qw, current);
        if (qw == null) {
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
        return PageResponse.from(result, this::toVO);
    }

    /** 返回 null（通过包装空查询语义）表示无可见数据 */
    private void applyScope(QueryWrapper<PerformanceReview> qw, LoginUser current) {
        String scope = current.getDataScope();
        if ("ALL".equals(scope) || "DEPARTMENT".equals(scope)) {
            return; // 管理员/人事可看全部
        }
        if (current.getEmployeeId() == null) {
            qw.eq("id", -1L);
            return;
        }
        if ("PROJECT".equals(scope)) {
            // 项目经理：自己作为评价人的记录 + 自己作为被评价人的记录
            qw.and(w -> w.eq("reviewer_employee_id", current.getEmployeeId())
                    .or().eq("employee_id", current.getEmployeeId()));
        } else {
            // SELF：仅本人被评价且已提交/退回的记录
            qw.eq("employee_id", current.getEmployeeId())
                    .in("status", ReviewStatus.SUBMITTED.name(), ReviewStatus.RETURNED.name());
        }
    }

    @Override
    public PerformanceReviewVO detail(Long id) {
        PerformanceReview review = requireReview(id);
        LoginUser current = SecurityUtils.getCurrentUser();
        if ("ALL".equals(current.getDataScope()) || "DEPARTMENT".equals(current.getDataScope())) {
            return toVO(review);
        }
        boolean involved = review.getEmployeeId().equals(current.getEmployeeId())
                || review.getReviewerEmployeeId().equals(current.getEmployeeId());
        if (!involved) {
            throw BusinessException.forbidden("无权查看该绩效记录");
        }
        // 普通员工仅能看到已提交的评价
        if (review.getEmployeeId().equals(current.getEmployeeId())
                && ReviewStatus.DRAFT.name().equals(review.getStatus())
                && !review.getReviewerEmployeeId().equals(current.getEmployeeId())) {
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
        reviewMapper.insert(review);
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
        review.setProjectTeamId(request.getProjectTeamId());
        review.setPeriodType(request.getPeriodType());
        review.setPeriodKey(request.getPeriodKey());
        review.setScore(request.getScore());
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        if (ReviewStatus.RETURNED.name().equals(review.getStatus())) {
            review.setStatus(ReviewStatus.DRAFT.name());
        }
        reviewMapper.updateById(review);
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
        review.setStatus(ReviewStatus.SUBMITTED.name());
        review.setSubmittedAt(OffsetDateTime.now());
        reviewMapper.updateById(review);
        auditService.success("performance:submit", "performance_review", id, "提交绩效评价");
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
        return PageResponse.from(result, this::toVO);
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
                && ("ALL".equals(current.getDataScope()) || "DEPARTMENT".equals(current.getDataScope()))) {
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
                && ("ALL".equals(current.getDataScope()));
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

    private PerformanceReviewVO toVO(PerformanceReview r) {
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
        Employee emp = employeeMapper.selectById(r.getEmployeeId());
        if (emp != null) {
            vo.setEmployeeName(emp.getEmployeeName());
            vo.setEmployeeNo(emp.getEmployeeNo());
        }
        Employee reviewer = employeeMapper.selectById(r.getReviewerEmployeeId());
        vo.setReviewerName(reviewer == null ? null : reviewer.getEmployeeName());
        if (r.getProjectTeamId() != null) {
            ProjectTeam team = teamMapper.selectById(r.getProjectTeamId());
            vo.setProjectTeamName(team == null ? null : team.getProjectName());
        }
        return vo;
    }
}
