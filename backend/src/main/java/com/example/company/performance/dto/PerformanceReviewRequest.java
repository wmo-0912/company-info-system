package com.example.company.performance.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PerformanceReviewRequest {

    @NotNull(message = "被评价员工不能为空")
    private Long employeeId;

    private Long projectTeamId;

    @NotBlank(message = "周期类型不能为空")
    @Pattern(regexp = "MONTHLY|QUARTERLY", message = "周期类型必须是 MONTHLY 或 QUARTERLY")
    private String periodType;

    @NotBlank(message = "周期不能为空")
    @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2]|Q[1-4])$", message = "周期格式应为 YYYY-MM 或 YYYY-Qn")
    private String periodKey;

    @DecimalMin(value = "0", message = "评分范围 0-100")
    @DecimalMax(value = "100", message = "评分范围 0-100")
    private BigDecimal score;

    @Size(max = 20)
    private String rating;

    @Size(max = 2000)
    private String comment;
}
