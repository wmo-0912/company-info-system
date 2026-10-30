package com.example.company.performance.service;

import com.example.company.common.api.PageResponse;
import com.example.company.performance.vo.PerformanceAppealVO;

public interface PerformanceAppealService {

    /** 员工对自己收到的已提交评价提出申诉 */
    PerformanceAppealVO create(Long reviewId, String reason);

    /** 申诉分页（人事/管理员处理用，受数据范围约束） */
    PageResponse<PerformanceAppealVO> page(int page, int pageSize, String status);

    /** 我提出的申诉 */
    PageResponse<PerformanceAppealVO> my(int page, int pageSize);

    /** 受理（把评价退回评价人复核）或驳回，必须填写处理意见 */
    PerformanceAppealVO handle(Long id, String decision, String comment);
}
