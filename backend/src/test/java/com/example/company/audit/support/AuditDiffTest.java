package com.example.company.audit.support;

import com.example.company.employee.entity.Employee;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AuditDiffTest {

    private Employee employee() {
        Employee e = new Employee();
        e.setId(1L);
        e.setEmployeeNo("E0001");
        e.setEmployeeName("张三");
        e.setDepartmentId(10L);
        e.setPositionName("工程师");
        e.setPhone("13800001111");
        e.setHireDate(LocalDate.of(2024, 1, 2));
        return e;
    }

    @Test
    @DisplayName("无字段变化时 diff 为空串")
    void returnsEmptyWhenNothingChanged() {
        Employee before = employee();
        AuditDiff.Snapshot snapshot = AuditDiff.snapshot(before);

        Employee after = employee();

        assertThat(AuditDiff.describe(snapshot, after)).isEmpty();
    }

    @Test
    @DisplayName("字段变化输出 旧值→新值，且不含未登记字段")
    void describesChangedFieldsOnly() {
        Employee before = employee();
        AuditDiff.Snapshot snapshot = AuditDiff.snapshot(before);

        Employee after = employee();
        after.setId(99L);              // id 未登记，不应出现
        after.setPositionName("高级工程师");
        after.setHireDate(LocalDate.of(2024, 3, 1));
        after.setCreatedAt(null);      // 未登记字段，不应出现

        String diff = AuditDiff.describe(snapshot, after);

        assertThat(diff).isEqualTo("岗位 工程师→高级工程师；入职日期 2024-01-02→2024-03-01");
        assertThat(diff).doesNotContain("99");
    }

    @Test
    @DisplayName("手机号、邮箱、住址等敏感字段只记录掩码值")
    void masksSensitiveFields() {
        Employee before = employee();
        AuditDiff.Snapshot snapshot = AuditDiff.snapshot(before);

        Employee after = employee();
        after.setPhone("13900002222");
        after.setEmail("zhangsan@example.com");

        String diff = AuditDiff.describe(snapshot, after);

        assertThat(diff).contains("手机号 138****1111→139****2222");
        assertThat(diff).doesNotContain("13900002222");
        assertThat(diff).doesNotContain("zhangsan@example.com");
    }

    @Test
    @DisplayName("空值变化显示为（空）")
    void showsEmptyMarkerForNullValues() {
        Employee before = employee();
        before.setRemark("原备注");
        AuditDiff.Snapshot snapshot = AuditDiff.snapshot(before);

        Employee after = employee();
        after.setRemark(null);

        assertThat(AuditDiff.describe(snapshot, after)).isEqualTo("备注 原备注→（空）");
    }

    @Test
    @DisplayName("格式化器把部门 ID 换成部门名")
    void usesFormatterForDepartment() {
        Employee before = employee();
        AuditDiff.Snapshot snapshot = AuditDiff.snapshot(before);

        Employee after = employee();
        after.setDepartmentId(20L);

        String diff = AuditDiff.describe(snapshot, after, (field, value) ->
                "departmentId".equals(field) ? ("D" + value) : null);

        assertThat(diff).isEqualTo("部门 D10→D20");
    }

    @Test
    @DisplayName("格式化器返回 null 时回退到原始值")
    void fallsBackToPlainValueWhenFormatterReturnsNull() {
        Employee before = employee();
        AuditDiff.Snapshot snapshot = AuditDiff.snapshot(before);

        Employee after = employee();
        after.setDepartmentId(20L);

        assertThat(AuditDiff.describe(snapshot, after, (field, value) -> null)).isEqualTo("部门 10→20");
    }

    @Test
    @DisplayName("变化项过多时截断并标注总项数")
    void truncatesLongDiff() {
        Employee before = employee();
        AuditDiff.Snapshot snapshot = AuditDiff.snapshot(before);

        Employee after = employee();
        after.setEmployeeName("张".repeat(60));
        after.setPositionName("岗".repeat(60));
        after.setAddress("地".repeat(60));
        after.setEducationCode("E".repeat(60));
        after.setPositionLevelCode("L".repeat(60));
        after.setRemark("注".repeat(60));
        after.setGender("M".repeat(60));
        after.setEmployeeNo("N".repeat(60));
        after.setPhone("1".repeat(30));
        after.setEmail("e".repeat(60));

        String diff = AuditDiff.describe(snapshot, after);

        assertThat(diff.length()).isLessThanOrEqualTo(540);
        assertThat(diff).contains("共 10 项变更");
    }
}
