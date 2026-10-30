package com.example.company.performance.service;

import com.example.company.common.api.PageResponse;
import com.example.company.performance.dto.PerformanceReviewRequest;
import com.example.company.performance.vo.PerformanceReviewVO;

public interface PerformanceService {

    PageResponse<PerformanceReviewVO> page(int page, int pageSize, String periodType, String periodKey, String status);

    PerformanceReviewVO detail(Long id);

    PerformanceReviewVO create(PerformanceReviewRequest request);

    PerformanceReviewVO update(Long id, PerformanceReviewRequest request);

    PerformanceReviewVO submit(Long id);

    PageResponse<PerformanceReviewVO> my(int page, int pageSize);
}
