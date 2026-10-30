package com.example.company.common.enums;

public enum EmployeeStatus {
    /**
     * 员工在职状态。调岗不是状态：调岗只改部门/岗位，状态在 PROBATION 与 REGULAR 之间不变，
     * 事件本身记在 employee_employment_record 与审计日志里。
     */
    PENDING, PROBATION, REGULAR, RESIGNED
}
