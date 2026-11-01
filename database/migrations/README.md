# 数据库升级脚本（migrations）

## 一句话说明

这个目录放的是「给**已经在跑的老库**打补丁」的脚本。

后端代码每次给数据库**新增了表或字段**，就会在这里放一个脚本；上线时把这些脚本跑一遍，让服务器上的老库跟上新代码。

- **老库**（已经在用的）：按下面的顺序跑这些脚本。
- **全新空库**：不用跑这里的东西，直接执行 `database/schema.sql` 就行（这些脚本的内容都已经包含在 schema.sql 里了）。

> ⚠️ 如果只换了后端 jar、却没跑这里的脚本，登录就会报 **500**，服务器日志里是类似
> `错误: 字段 "must_change_password" 不存在`（或别的表名/字段名）。这是最容易踩的坑。

---

## 本次要跑哪些脚本（按这个顺序）

| 顺序 | 文件名 | 它做了什么 |
|---|---|---|
| 1 | `2026-10-30-must-change-password.sql` | `sys_user` 加「是否必须改密」字段 |
| 2 | `2026-10-30-token-version.sql` | `sys_user` 加「会话版本」字段（改密后旧登录立刻失效） |
| 3 | `2026-10-30-password-history.sql` | 新建「历史密码」表（防止改回最近 5 个旧密码） |
| 4 | `2026-10-30-remove-transferred-status.sql` | 把历史「已调岗」状态统一改成「正式员工」 |
| 5 | `2026-10-30-menu-driven-permissions.sql` | 修正左侧菜单的分组和跳转地址 |
| 6 | `2026-10-30-performance-period-lock.sql` | 新建「绩效周期锁定」表 |
| 7 | `2026-10-30-role-module-scope.sql` | 新建「角色模块数据范围」表 |
| 8 | `2026-10-30-onboard-request-null-employee.sql` | 入职申请允许先不关联员工 |
| 9 | `2026-10-30-performance-appeal.sql` | 新建「绩效申诉」表 |

> 每个脚本都能重复执行（都用了 `IF NOT EXISTS`），重复跑不会报错。

---

## 怎么跑（Windows 本地）

下面的命令都在项目根目录 `D:\project\company-info-system` 下、用 PowerShell 执行。
如果提示找不到 `psql`，把 `psql` 换成完整路径 `D:\developtool\PostgreSQL\17\bin\psql.exe`。

### 方法一（推荐）：先合并成一个文件，再跑

文件越少越不容易漏，所以上线时**把上面 9 个脚本合并成一个文件**，一次跑完、出错整体回滚。

> ✅ 当前版本的合并文件已经生成好了，就是 **`operations/upgrade_20261031_all.sql`**，直接用即可。

如果以后又新增了脚本，按下面的 PowerShell 重新合并一次：

```powershell
# ① 按顺序列出本次要跑的脚本（新增脚本就加进这个列表）
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

# ② 把每个文件自带的 BEGIN;/COMMIT; 去掉（由外面统一包一层事务）
$body = foreach ($f in $files) {
  "`n-- ====== 来自 $f ======"
  (Get-Content -Raw $f) -replace '(?m)^\s*(BEGIN|COMMIT);\s*$', ''
}

# ③ 外面包一层 BEGIN/COMMIT，末尾加自检，生成合并文件
$selfcheck = @'

-- ============ 升级后自检（期望 3 / 4 / 20）============
SELECT count(*) AS sys_user_new_cols FROM information_schema.columns
 WHERE table_name='sys_user' AND column_name IN ('must_change_password','token_version','failed_login_count');
SELECT count(*) AS new_tables FROM information_schema.tables
 WHERE table_schema='public' AND table_name IN ('sys_password_history','performance_period','performance_appeal','sys_role_module_scope');
SELECT count(*) AS public_tables FROM pg_tables WHERE schemaname='public';
'@

$sql = "SET client_encoding = 'UTF8';`nBEGIN;`n$body`nCOMMIT;`n$selfcheck"
[System.IO.File]::WriteAllText("$PWD\operations\upgrade_20261031_all.sql", $sql, (New-Object System.Text.UTF8Encoding $false))
Write-Host "已生成：operations\upgrade_20261031_all.sql"
```

### 方法二：逐个文件跑（只改了一个脚本、或本地测试时）

```powershell
$env:PGPASSWORD = "<本地数据库口令>"   # 见 backend\src\main\resources\application-local.yml 里的 password
foreach ($f in $files) {
  psql -h 127.0.0.1 -U company_app -d company_info -v ON_ERROR_STOP=1 -f $f
  if ($LASTEXITCODE -ne 0) { throw "执行失败: $f" }
}
```

### 方法三：先试跑、不真正提交（最安全，改库前都建议做）

把合并文件末尾的 `COMMIT;` 临时换成 `ROLLBACK;` 再跑，跑完不会留下任何改动：

```powershell
$env:PGPASSWORD = "<本地数据库口令>"
(Get-Content -Raw 'operations\upgrade_20261031_all.sql') -replace 'COMMIT;','ROLLBACK;' |
  Set-Content "$env:TEMP\upgrade_test.sql" -Encoding UTF8
psql -h 127.0.0.1 -U company_app -d company_info -v ON_ERROR_STOP=1 -f "$env:TEMP\upgrade_test.sql"
# 看到自检 3 / 4 / 20 且没报错 = 脚本没问题，可以正式跑了
```

---

## 上线到服务器

合并好的文件传到服务器后执行。**具体命令在 `operations/operations-manual.md` 的「§3.0 数据库更新到底运行什么命令」**，核心就一条：

```bash
sudo -u postgres psql -d company_info -v ON_ERROR_STOP=1 -f /tmp/upgrade.sql
```

---

## 跑完怎么确认成功

脚本末尾会输出 3 个自检数字，期望值分别是：

- `sys_user_new_cols` = **3**（两个新字段 + 原来的 `failed_login_count` 都在）
- `new_tables` = **4**（4 张新表都建好了）
- `public_tables` = **20**（整个库里一共 20 张表，一张不少）

三个都对上，就说明数据库升级完成。

---

## 注意事项

- 脚本都幂等，重复跑不会报错。
- **第 4 个脚本会改历史数据**（把「已调岗」改成「正式员工」），执行前先备份：
  `pg_dump -Fc company_info > backup.dump`
- 第 5 个脚本改的是菜单数据，前提是库里已经初始化过权限点；**空库不要跑这些**，直接用 `schema.sql`。
- 本地（Windows）用项目自带的 PostgreSQL 跑；服务器（Linux）用上面的 `psql` 命令，执行身份和修属主见 operations-manual.md §3.0。
