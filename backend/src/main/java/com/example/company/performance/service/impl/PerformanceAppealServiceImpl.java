package com.example.company.performance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.company.audit.service.AuditService;
import com.example.company.common.api.PageResponse;
import com.example.company.common.enums.AppealStatus;
import com.example.company.common.enums.DataModule;
import com.example.company.common.enums.ReviewStatus;
import com.example.company.common.exception.BusinessException;
import com.example.company.common.security.DataScopeService;
import com.example.company.common.security.LoginUser;
import com.example.company.common.security.SecurityUtils;
import com.example.company.employee.entity.Employee;
import com.example.company.employee.mapper.EmployeeMapper;
import com.example.company.performance.entity.PerformanceAppeal;
import com.example.company.performance.entity.PerformanceReview;
import com.example.company.performance.mapper.PerformanceAppealMapper;
import com.example.company.performance.mapper.PerformanceReviewMapper;
import com.example.company.performance.service.PerformanceAppealService;
import com.example.company.performance.vo.PerformanceAppealVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PerformanceAppealServiceImpl implements PerformanceAppealService {

    private static final int MAX_TEXT = 500;

    private final PerformanceAppealMapper appealMapper;
    private final PerformanceReviewMapper reviewMapper;
    private final EmployeeMapper employeeMapper;
    private final DataScopeService dataScopeService;
    private final AuditService auditService;

    @Override
    @Transactional
    public PerformanceAppealVO create(Long reviewId, String reason) {
        LoginUser current = SecurityUtils.getCurrentUser();
        Long me = current.getEmployeeId();
        if (me == null) {
            throw BusinessException.forbidden("当前账号未关联员工档案，无法提出申诉");
        }
        PerformanceReview review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw BusinessException.notFound("绩效记录不存在");
        }
        if (!me.equals(review.getEmployeeId())) {
            throw BusinessException.forbidden("只能对自己收到的绩效评价提出申诉");
        }
        if (!ReviewStatus.SUBMITTED.name().equals(review.getStatus())) {
            throw BusinessException.unprocessable("只有已提交的评价可以申诉");
        }
        String text = normalize(reason, "请填写申诉理由");
        Long pending = appealMapper.selectCount(new QueryWrapper<PerformanceAppeal>()
                .eq("review_id", reviewId)
                .eq("status", AppealStatus.PENDING.name()));
        if (pending != null && pending > 0) {
            throw BusinessException.conflict("该评价已有待处理的申诉，请等待人事处理");
        }
        PerformanceAppeal appeal = new PerformanceAppeal();
        appeal.setReviewId(reviewId);
        appeal.setEmployeeId(me);
        appeal.setReason(text);
        appeal.setStatus(AppealStatus.PENDING.name());
        appealMapper.insert(appeal);
        auditService.success("performance:appeal", "performance_appeal", appeal.getId(),
                "对绩效评价 " + reviewId + "（周期 " + review.getPeriodKey() + "）提出申诉");
        return toVO(appeal);
    }

    @Override
    public PageResponse<PerformanceAppealVO> page(int page, int pageSize, String status) {
        LoginUser current = SecurityUtils.getCurrentUser();
        Set<Long> visible = dataScopeService.visibleEmployeeIds(current, DataModule.PERFORMANCE);
        if (visible != null && visible.isEmpty()) {
            return PageResponse.of(List.of(), page, pageSize, 0);
        }
        QueryWrapper<PerformanceAppeal> qw = new QueryWrapper<>();
        if (visible != null) {
            qw.in("employee_id", visible);
        }
        if (StringUtils.hasText(status)) {
            qw.eq("status", status.trim());
        }
        qw.orderByDesc("created_at");
        Page<PerformanceAppeal> result = appealMapper.selectPage(new Page<>(page, pageSize), qw);
        return PageResponse.from(result, this::toVO);
    }

    @Override
    public PageResponse<PerformanceAppealVO> my(int page, int pageSize) {
        LoginUser current = SecurityUtils.getCurrentUser();
        if (current.getEmployeeId() == null) {
            return PageResponse.of(List.of(), page, pageSize, 0);
        }
        Page<PerformanceAppeal> result = appealMapper.selectPage(new Page<>(page, pageSize),
                new QueryWrapper<PerformanceAppeal>()
                        .eq("employee_id", current.getEmployeeId())
                        .orderByDesc("created_at"));
        return PageResponse.from(result, this::toVO);
    }

    @Override
    @Transactional
    public PerformanceAppealVO handle(Long id, String decision, String comment) {
        AppealStatus target = parseDecision(decision);
        PerformanceAppeal appeal = appealMapper.selectById(id);
        if (appeal == null) {
            throw BusinessException.notFound("申诉记录不存在");
        }
        if (!AppealStatus.PENDING.name().equals(appeal.getStatus())) {
            throw BusinessException.unprocessable("该申诉已处理，不能重复处理");
        }
        Long handler = SecurityUtils.getCurrentUser().getEmployeeId();
        if (handler != null && handler.equals(appeal.getEmployeeId())) {
            throw BusinessException.forbidden("不能处理自己提出的申诉");
        }
        String text = StringUtils.hasText(comment) ? comment.trim() : null;
        if (target == AppealStatus.REJECTED && text == null) {
            throw BusinessException.badRequest("驳回申诉必须填写处理意见");
        }
        if (text != null && text.length() > MAX_TEXT) {
            throw BusinessException.badRequest("处理意见不能超过 " + MAX_TEXT + " 个字符");
        }
        if (target == AppealStatus.ACCEPTED) {
            PerformanceReview review = reviewMapper.selectById(appeal.getReviewId());
            if (review == null) {
                throw BusinessException.notFound("关联的绩效评价不存在");
            }
            // 受理即把评价退回评价人复核：申诉不应直接改分，仍走「退回 → 改稿 → 重新提交」
            if (!ReviewStatus.RETURNED.name().equals(review.getStatus())) {
                review.setStatus(ReviewStatus.RETURNED.name());
                reviewMapper.updateById(review);
            }
        }
        appeal.setStatus(target.name());
        appeal.setHandlerEmployeeId(handler);
        appeal.setHandleComment(text);
        appeal.setHandledAt(OffsetDateTime.now());
        appealMapper.updateById(appeal);
        auditService.success("performance:appeal-handle", "performance_appeal", id,
                (target == AppealStatus.ACCEPTED ? "受理申诉并退回评价人复核" : "驳回申诉")
                        + "：评价 #" + appeal.getReviewId() + (text == null ? "" : "；意见：" + text));
        return toVO(appeal);
    }

    private AppealStatus parseDecision(String decision) {
        if (!StringUtils.hasText(decision)) {
            throw BusinessException.badRequest("处理结果不能为空");
        }
        String value = decision.trim().toUpperCase(java.util.Locale.ROOT);
        if ("ACCEPT".equals(value)) {
            return AppealStatus.ACCEPTED;
        }
        if ("REJECT".equals(value)) {
            return AppealStatus.REJECTED;
        }
        throw BusinessException.badRequest("处理结果只能是 ACCEPT 或 REJECT");
    }

    private String normalize(String reason, String emptyMessage) {
        if (!StringUtils.hasText(reason)) {
            throw BusinessException.badRequest(emptyMessage);
        }
        String text = reason.trim();
        if (text.length() > MAX_TEXT) {
            throw BusinessException.badRequest("申诉理由不能超过 " + MAX_TEXT + " 个字符");
        }
        return text;
    }

    private PerformanceAppealVO toVO(PerformanceAppeal appeal) {
        PerformanceAppealVO vo = new PerformanceAppealVO();
        vo.setId(appeal.getId());
        vo.setReviewId(appeal.getReviewId());
        vo.setEmployeeId(appeal.getEmployeeId());
        vo.setReason(appeal.getReason());
        vo.setStatus(appeal.getStatus());
        vo.setHandlerEmployeeId(appeal.getHandlerEmployeeId());
        vo.setHandleComment(appeal.getHandleComment());
        vo.setHandledAt(appeal.getHandledAt());
        vo.setCreatedAt(appeal.getCreatedAt());
        vo.setEmployeeName(employeeName(appeal.getEmployeeId()));
        vo.setHandlerName(employeeName(appeal.getHandlerEmployeeId()));
        PerformanceReview review = appeal.getReviewId() == null ? null : reviewMapper.selectById(appeal.getReviewId());
        if (review != null) {
            vo.setReviewerEmployeeId(review.getReviewerEmployeeId());
            vo.setReviewerName(employeeName(review.getReviewerEmployeeId()));
            vo.setPeriodType(review.getPeriodType());
            vo.setPeriodKey(review.getPeriodKey());
            vo.setScore(review.getScore());
        }
        return vo;
    }

    private String employeeName(Long employeeId) {
        if (employeeId == null) {
            return null;
        }
        Employee employee = employeeMapper.selectById(employeeId);
        return employee == null ? null : employee.getEmployeeName();
    }
}
