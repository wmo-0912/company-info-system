package com.example.company.performance.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 绩效复核退回请求：退回原因只记入审计日志 */
@Data
public class ReturnReviewRequest {

    @Size(max = 500, message = "退回原因不能超过 500 字")
    private String reason;
}
