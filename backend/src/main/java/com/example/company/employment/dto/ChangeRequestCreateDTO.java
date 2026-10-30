package com.example.company.employment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.Map;

@Data
public class ChangeRequestCreateDTO {

    /**
     * 员工 ID。入职（ONBOARD）申请允许为空：此时新员工信息放在 payload 里，批准时才建档并开通账号。
     */
    private Long employeeId;

    @NotNull(message = "变更类型不能为空")
    private String changeType; // ONBOARD / REGULARIZE / TRANSFER / RESIGN

    @NotNull(message = "生效日期不能为空")
    private LocalDate effectiveDate;

    @Size(max = 2000)
    private String reason;

    /**
     * 类型相关的附加信息：
     * 调岗填 targetDepartmentId / targetPositionName；入职且未指定员工时填
     * employeeNo / employeeName / departmentId（必填）与 gender / birthDate / phone / email /
     * positionName / positionLevelCode / educationCode（可选）。
     */
    private Map<String, Object> payload;
}
