package com.example.company.employment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.Map;

@Data
public class ChangeRequestCreateDTO {

    @NotNull(message = "员工不能为空")
    private Long employeeId;

    @NotNull(message = "变更类型不能为空")
    private String changeType; // ONBOARD / REGULARIZE / TRANSFER / RESIGN

    @NotNull(message = "生效日期不能为空")
    private LocalDate effectiveDate;

    @Size(max = 2000)
    private String reason;

    /** 调岗时必填 targetDepartmentId，可选 targetPositionName */
    private Map<String, Object> payload;
}
