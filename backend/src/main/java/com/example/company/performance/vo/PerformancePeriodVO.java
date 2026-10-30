package com.example.company.performance.vo;

import java.time.OffsetDateTime;

/** 绩效周期状态：locked=true 时该周期内不能再新增/修改/提交评价 */
public record PerformancePeriodVO(String periodKey, boolean locked, OffsetDateTime lockedAt, Long lockedBy) {
}
