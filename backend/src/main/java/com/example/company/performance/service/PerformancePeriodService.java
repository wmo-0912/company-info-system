package com.example.company.performance.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.company.audit.service.AuditService;
import com.example.company.common.exception.BusinessException;
import com.example.company.common.security.SecurityUtils;
import com.example.company.performance.entity.PerformancePeriod;
import com.example.company.performance.mapper.PerformancePeriodMapper;
import com.example.company.performance.vo.PerformancePeriodVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 绩效周期锁定：锁定后该周期内的评价不能再新增/修改/提交，
 * 用来替代"发完绩效又被人改回去"的口头约定。
 */
@Service
@RequiredArgsConstructor
public class PerformancePeriodService {

    /** 与 performance_period.period_key 列宽一致 */
    private static final int MAX_PERIOD_KEY_LENGTH = 20;

    private final PerformancePeriodMapper periodMapper;
    private final AuditService auditService;

    /** 周期被锁定返回 true；未登记的周期视为未锁定 */
    public boolean isLocked(String periodKey) {
        if (!StringUtils.hasText(periodKey)) {
            return false;
        }
        PerformancePeriod period = find(periodKey.trim());
        return period != null && Boolean.TRUE.equals(period.getLocked());
    }

    /** 锁定/解锁：不存在则登记，存在则改状态；解锁后保留登记行，便于查询历史锁定时间 */
    @Transactional
    public PerformancePeriodVO setLocked(String periodKey, boolean locked) {
        String key = normalize(periodKey);
        PerformancePeriod period = find(key);
        if (period == null) {
            period = new PerformancePeriod();
            period.setPeriodKey(key);
            period.setLocked(locked);
            period.setLockedAt(locked ? OffsetDateTime.now() : null);
            period.setLockedBy(locked ? currentUserId() : null);
            periodMapper.insert(period);
        } else {
            if (Boolean.valueOf(locked).equals(period.getLocked())) {
                return toVO(period);
            }
            period.setLocked(locked);
            period.setLockedAt(locked ? OffsetDateTime.now() : null);
            period.setLockedBy(locked ? currentUserId() : null);
            period.setUpdatedAt(OffsetDateTime.now());
            periodMapper.updateById(period);
        }
        auditService.success(locked ? "performance:period-lock" : "performance:period-unlock",
                "performance_period", key,
                (locked ? "锁定" : "解锁") + "绩效周期 " + key);
        return toVO(period);
    }

    public List<PerformancePeriodVO> list() {
        return periodMapper.selectList(new QueryWrapper<PerformancePeriod>()
                        .orderByDesc("id"))
                .stream()
                .map(this::toVO)
                .toList();
    }

    private PerformancePeriod find(String key) {
        return periodMapper.selectOne(new QueryWrapper<PerformancePeriod>()
                .eq("period_key", key)
                .last("limit 1"));
    }

    private String normalize(String periodKey) {
        if (!StringUtils.hasText(periodKey)) {
            throw BusinessException.badRequest("周期标识不能为空，例如 2026-Q3");
        }
        String key = periodKey.trim();
        if (key.length() > MAX_PERIOD_KEY_LENGTH) {
            throw BusinessException.badRequest("周期标识不能超过 " + MAX_PERIOD_KEY_LENGTH + " 个字符");
        }
        return key;
    }

    private Long currentUserId() {
        return SecurityUtils.getCurrentUserOrNull() == null ? null : SecurityUtils.getCurrentUserOrNull().getUserId();
    }

    private PerformancePeriodVO toVO(PerformancePeriod period) {
        return new PerformancePeriodVO(period.getPeriodKey(), Boolean.TRUE.equals(period.getLocked()),
                period.getLockedAt(), period.getLockedBy());
    }
}
