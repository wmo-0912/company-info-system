package com.example.company.performance.service;

import com.example.company.common.api.PageResponse;
import com.example.company.performance.dto.PerformanceReviewRequest;
import com.example.company.performance.vo.PerformanceReviewVO;
import com.example.company.performance.vo.PerformanceSummaryVO;

public interface PerformanceService {

    PageResponse<PerformanceReviewVO> page(int page, int pageSize, String periodType, String periodKey, String status);

    PerformanceReviewVO detail(Long id);

    PerformanceReviewVO create(PerformanceReviewRequest request);

    PerformanceReviewVO update(Long id, PerformanceReviewRequest request);

    PerformanceReviewVO submit(Long id);

    /** 人事复核退回：仅已提交的评价可退回，退回后由评价人改稿并重新提交 */
    PerformanceReviewVO returnForRevision(Long id, String reason);

    PageResponse<PerformanceReviewVO> my(int page, int pageSize);

    /** 按周期汇总已提交的评价（平均分、分数段、部门/项目组维度），受数据范围约束 */
    PerformanceSummaryVO summary(String periodType, String periodKey);
}
