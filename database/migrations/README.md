# 存量库升级脚本

本项目的表结构以 `database/schema.sql` 为准，但不引入 Flyway/Liquibase（见 README「设计假设 2」），
因此**已上线的库升级时要按顺序手动执行本目录的脚本**；新建空库直接执行 `schema.sql` 即可，本目录的文件都已包含在其中。

> 只跑 `schema.sql` 升级旧库是行不通的：`schema.sql` 用 `CREATE TABLE`，对已存在的表不会补列。
> 典型症状就是登录报 `org.postgresql.util.PSQLException: 错误: 字段 "must_change_password" 不存在`。

## 执行顺序

| 顺序 | 脚本 | 内容 | 幂等 |
|---|---|---|---|
| 1 | `2026-10-30-must-change-password.sql` | `sys_user.must_change_password` 列 | 是 |
| 2 | `2026-10-30-token-version.sql` | `sys_user.token_version` 列（改密后旧 token 失效） | 是 |
| 3 | `2026-10-30-password-history.sql` | `sys_password_history` 表与索引 | 是 |
| 4 | `2026-10-30-remove-transferred-status.sql` | 历史 `TRANSFERRED` 状态回落为 `REGULAR` | 是 |
| 5 | `2026-10-30-menu-driven-permissions.sql` | 补「系统管理」菜单分组、修正菜单路由 | 是 |
| 6 | `2026-10-30-performance-period-lock.sql` | `performance_period` 表 | 是 |
| 7 | `2026-10-30-role-module-scope.sql` | `sys_role_module_scope` 表 | 是 |
| 8 | `2026-10-30-onboard-request-null-employee.sql` | `employee_change_request.employee_id` 放开非空 | 是 |
| 9 | `2026-10-30-performance-appeal.sql` | `performance_appeal` 表与索引 | 是 |

## 一次性全部执行（Windows PowerShell）

```powershell
$env:PGPASSWORD = "<数据库口令>"
cd D:\code\project\company-info-system
$files = @(
  'database/migrations/2026-10-30-must-change-password.sql',
  'database/migrations/2026-10-30-token-version.sql',
  'database/migrations/2026-10-30-password-history.sql',
  'database/migrations/2026-10-30-remove-transferred-status.sql',
  'database/migrations/2026-10-30-menu-driven-permissions.sql',
  'database/migrations/2026-10-30-performance-period-lock.sql',
  'database/migrations/2026-10-30-role-module-scope.sql',
  'database/migrations/2026-10-30-onboard-request-null-employee.sql',
  'database/migrations/2026-10-30-performance-appeal.sql'
)
foreach ($f in $files) {
  psql -h localhost -U company_app -d company_info -v ON_ERROR_STOP=1 -f $f
  if ($LASTEXITCODE -ne 0) { throw "执行失败: $f" }
}
```

Linux / macOS 等价写法：

```bash
export PGPASSWORD='<数据库口令>'
for f in \
  database/migrations/2026-10-30-must-change-password.sql \
  database/migrations/2026-10-30-token-version.sql \
  database/migrations/2026-10-30-password-history.sql \
  database/migrations/2026-10-30-remove-transferred-status.sql \
  database/migrations/2026-10-30-menu-driven-permissions.sql \
  database/migrations/2026-10-30-performance-period-lock.sql \
  database/migrations/2026-10-30-role-module-scope.sql \
  database/migrations/2026-10-30-onboard-request-null-employee.sql \
  database/migrations/2026-10-30-performance-appeal.sql ; do
  psql -h localhost -U company_app -d company_info -v ON_ERROR_STOP=1 -f "$f" || break
done
```

## 校验

```sql
-- 应能查到 3 列
SELECT column_name FROM information_schema.columns
WHERE table_name = 'sys_user'
  AND column_name IN ('must_change_password', 'token_version', 'failed_login_count');

-- 应能查到 4 张新表
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN ('sys_password_history', 'performance_period', 'performance_appeal', 'sys_role_module_scope');
```

## 注意事项

- 每条脚本都带了 `BEGIN/COMMIT` 或幂等的 `IF NOT EXISTS`，重复执行不会报错。
- `2026-10-30-remove-transferred-status.sql` 会改历史数据，执行前先 `pg_dump -Fc company_info > backup.dbc`。
- `2026-10-30-menu-driven-permissions.sql` 改的是 `sys_permission` 数据，依赖初始化时已插入菜单权限点；空库请直接用 `schema.sql` + 应用启动时的 `DevDataInitializer` 同步。
- 新增列后无需重启应用之外的额外操作；登录报错的直接原因就是缺列，补齐即可恢复。
