package com.example.company.audit.support;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 审计变更比对工具：把实体关键字段在写入前后的差异压成一行可读文本，作为 audit summary 的一部分。
 *
 * <p>设计取舍：项目没有独立的变更历史表，sys_audit_log.summary 是 TEXT，足以容纳字段级 diff，
 * 因此这里不引入 JSONB 列与数据库迁移，直接把"字段 旧值→新值"写进 summary。
 * 只比对 {@link #LABELS} 里登记过的字段，避免 id / created_at / updated_at 之类的噪声进入审计。</p>
 */
public final class AuditDiff {

    /** 字段 -> 中文名。未登记的字段不参与比对。 */
    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    /** 需要脱敏的字段：只记录掩码后的值，避免审计日志本身成为敏感数据的泄漏点。 */
    private static final Set<String> SENSITIVE = new LinkedHashSet<>();

    /** 单个值的最大展示长度 */
    private static final int MAX_VALUE_LENGTH = 64;

    /** 整段 diff 的最大长度，超出后截断并追加"等 N 项" */
    private static final int MAX_DIFF_LENGTH = 500;

    static {
        // 员工档案
        LABELS.put("employeeNo", "工号");
        LABELS.put("employeeName", "姓名");
        LABELS.put("gender", "性别");
        LABELS.put("birthDate", "出生日期");
        LABELS.put("phone", "手机号");
        LABELS.put("email", "邮箱");
        LABELS.put("address", "住址");
        LABELS.put("emergencyContactName", "紧急联系人");
        LABELS.put("emergencyContactPhone", "紧急联系电话");
        LABELS.put("educationCode", "学历");
        LABELS.put("departmentId", "部门");
        LABELS.put("positionName", "岗位");
        LABELS.put("positionLevelCode", "职级");
        LABELS.put("managerEmployeeId", "直属主管");
        LABELS.put("hireDate", "入职日期");
        LABELS.put("employmentStatus", "在职状态");
        LABELS.put("resignationDate", "离职日期");
        LABELS.put("remark", "备注");
        // 系统账号
        LABELS.put("username", "登录名");
        LABELS.put("status", "状态");
        LABELS.put("departmentName", "部门");
        // 调整申请
        LABELS.put("changeType", "申请类型");
        LABELS.put("effectiveDate", "生效日期");
        LABELS.put("targetDepartmentId", "目标部门");
        LABELS.put("targetPositionName", "目标岗位");
        LABELS.put("reason", "申请原因");

        SENSITIVE.addAll(List.of("phone", "email", "address",
                "emergencyContactName", "emergencyContactPhone"));
    }

    private AuditDiff() {
    }

    /** 值的展示格式化：用于把部门 ID 换成部门名、员工 ID 换成姓名等。 */
    @FunctionalInterface
    public interface ValueFormatter {
        String format(String field, Object value);
    }

    private static final ValueFormatter DEFAULT_FORMATTER = (field, value) -> plain(value);

    /** 实体在修改前的字段快照。 */
    public record Snapshot(Map<String, Object> values) {
    }

    /** 采集实体快照。必须在修改实体之前调用。 */
    public static Snapshot snapshot(Object entity) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (entity != null) {
            for (Class<?> type = entity.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    int modifiers = field.getModifiers();
                    if (Modifier.isStatic(modifiers) || field.isSynthetic() || !LABELS.containsKey(field.getName())) {
                        continue;
                    }
                    field.setAccessible(true);
                    try {
                        values.putIfAbsent(field.getName(), field.get(entity));
                    } catch (IllegalAccessException e) {
                        // 读不到就跳过该字段，不影响主流程
                    }
                }
            }
        }
        return new Snapshot(values);
    }

    /** 生成"字段 旧值→新值；…"形式的差异描述；无变化时返回空串。 */
    public static String describe(Snapshot before, Object after) {
        return describe(before, after, DEFAULT_FORMATTER);
    }

    /** 生成差异描述，格式化器负责把 ID 等原始值换成可读名称。 */
    public static String describe(Snapshot before, Object after, ValueFormatter formatter) {
        if (before == null || after == null) {
            return "";
        }
        Snapshot current = snapshot(after);
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Object> entry : before.values().entrySet()) {
            String field = entry.getKey();
            Object oldValue = entry.getValue();
            Object newValue = current.values().get(field);
            if (Objects.equals(oldValue, newValue)) {
                continue;
            }
            boolean sensitive = SENSITIVE.contains(field);
            String oldText = sensitive ? mask(oldValue) : safeFormat(formatter, field, oldValue);
            String newText = sensitive ? mask(newValue) : safeFormat(formatter, field, newValue);
            parts.add(LABELS.get(field) + " " + oldText + "→" + newText);
        }
        if (parts.isEmpty()) {
            return "";
        }
        String joined = String.join("；", parts);
        if (joined.length() > MAX_DIFF_LENGTH) {
            joined = joined.substring(0, MAX_DIFF_LENGTH) + "…（共 " + parts.size() + " 项变更）";
        }
        return joined;
    }

    private static String safeFormat(ValueFormatter formatter, String field, Object value) {
        if (value == null) {
            return "（空）";
        }
        String text = formatter.format(field, value);
        if (text == null || text.isBlank()) {
            return plain(value);
        }
        return text;
    }

    private static String plain(Object value) {
        if (value == null) {
            return "（空）";
        }
        if (value instanceof Boolean bool) {
            return bool ? "是" : "否";
        }
        String text = String.valueOf(value);
        return text.length() > MAX_VALUE_LENGTH ? text.substring(0, MAX_VALUE_LENGTH) + "…" : text;
    }

    /** 掩码：保留前 3 位与后 4 位，其余用 * 代替。 */
    private static String mask(Object value) {
        if (value == null) {
            return "（空）";
        }
        String text = String.valueOf(value);
        if (text.isBlank()) {
            return "（空）";
        }
        if (text.length() <= 6) {
            return "***";
        }
        int keepTail = Math.min(4, text.length() - 3);
        return text.substring(0, 3) + "****" + text.substring(text.length() - keepTail);
    }
}
