# 公司基本信息管理系统

前后端分离的公司基本信息管理系统，覆盖组织架构、员工档案、入转调离、项目组、绩效、系统管理与审计日志等模块。

## 技术栈

| 端 | 技术 |
|---|---|
| 前端 | Vue 3 + Vite + TypeScript + Element Plus + Pinia + Vue Router + Axios |
| 后端 | Java 17 + Spring Boot 3 + Spring Security + JWT + MyBatis-Plus |
| 数据库 | PostgreSQL 17 |
| API | RESTful JSON，统一前缀 `/api/v1`，OpenAPI/Swagger 文档 |

## 目录结构

```
├─ backend/          # Spring Boot 后端（Maven Wrapper）
├─ frontend/         # Vue 3 前端
├─ database/         # schema.sql 建表脚本
└─ docs/             # 实施设计说明书、部署文档（含 Nginx 示例、测试账号、阿里云上线指南）
```

## 环境要求

- JDK 17+
- Node.js 20+
- PostgreSQL 17（也可用 14+，本系统以 17 验证）
- Maven 由 `backend/mvnw` 自动提供，无需全局安装

## 快速开始（开发环境）

### 1. 初始化数据库

```powershell
# 以 postgres 超级用户执行
psql -U postgres -c "CREATE USER company_app WITH PASSWORD 'your_password';"
psql -U postgres -c "CREATE DATABASE company_info OWNER company_app;"
psql -U postgres -d company_info -f database/schema.sql
```

### 2. 启动后端

后端配置通过环境变量注入。本地开发可直接使用 `application-local.yml`（已内置本地连接与演示数据开关）：

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

- 服务地址：`http://localhost:8080`
- `local` profile 下 `app.seed-demo-data=true`，首次启动在空库中自动写入演示数据（角色、权限、部门、员工、用户、项目组、字典等）
- Swagger UI（生产默认关闭）：`http://localhost:8080/swagger-ui/index.html`
  - 本地想看接口文档，启动时叠加 dev profile：`.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local,dev"`，或设 `SPRINGDOC_ENABLED=true`
  - 未开启时 `/swagger-ui/**`、`/v3/api-docs/**` 不注册路由并被安全链拦成 401

生产/自定义环境通过环境变量配置：

| 变量 | 说明 |
|---|---|
| `DB_URL` | JDBC 连接串，如 `jdbc:postgresql://localhost:5432/company_info` |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库账号密码 |
| `JWT_SECRET` | JWT 签名密钥，**至少 32 字节**，不足启动直接失败 |
| `SEED_DEMO_DATA` | 是否写入演示数据，生产必须为 `false` |

### 3. 启动前端

```powershell
cd frontend
npm install
npm run dev
```

- 开发地址：`http://localhost:5173`
- Vite 已配置 `/api` 代理到 `http://localhost:8080`

### 4. 构建与测试

```powershell
# 后端测试（119 个单元/切片测试，不依赖数据库）
cd backend; .\mvnw.cmd clean test

# 前端类型检查与生产构建
cd frontend; npm run type-check; npm run build
```

## 演示账号（仅开发环境）

以下账号由 `local` profile 的开发数据初始化器创建，数据库只存 BCrypt 哈希。**禁止在生产使用，生产启动时不会自动创建任何账号。**

完整清单（用户名、密码、角色、数据范围与管理建议）见 [docs/demo-accounts.md](docs/demo-accounts.md)。登录页不展示任何测试账号，避免向公网访客暴露默认口令。

新用户的初始密码由管理员创建用户时设置并**线下交付**（系统无邮件/短信服务，不会自动发送通知）。

新建账号、以及管理员重置密码后的账号默认标记为「需修改密码」：用户登录后只能进入个人中心，必须先完成改密才能使用其他功能。管理员忘记密码时可在「系统管理 → 用户管理 → 重置密码」生成一次性随机密码（仅在弹窗中显示一次，且不写入审计日志）。

## 角色权限矩阵（默认配置，严格对齐 D01 需求，可在角色管理中调整）

| 模块 | 管理员 | 人事 | 项目经理 | 普通员工 |
|---|---|---|---|---|
| 仪表盘 | ✓ | ✓ | ✓ | ✓ |
| 组织架构（部门） | 全部管理（新增/编辑/停用） | 仅查看 | 仅查看 | 无 |
| 员工档案 | 仅查看（创建账号时关联员工） | 查看/新增/编辑/归档 | 仅查看（数据范围限所属项目组） | 仅本人（个人中心） |
| 入转调离 | 无 | 申请 + 办理 | 无 | 无（个人中心可查本人任职记录） |
| 项目组 | 无 | 无 | 管理自己负责的项目组（含关闭/重开、成员加入与离开日期） | 仅查看与自己相关的项目组 |
| 绩效 | 查看全部 + 复核退回 | 查看全部 + 复核退回 | 评价项目组成员（草稿/已提交） | 仅查看本人非草稿评价 |
| 用户/角色/权限 | 管理（账号启停、角色启停与权限点分配；内置角色不可停用或改编码） | 无 | 无 | 无 |
| 数据字典 | 管理 | 查看 | 查看 | 查看必要字典 |
| 菜单 | 由权限点下发（`GET /api/v1/menus`），不再前端硬编码 | 同左 | 同左 | 同左 |
| 系统配置 | 管理（含公司名称 / Logo，保存后登录页与侧栏即时生效） | 无 | 无 | 无 |
| 审计日志 | 查看 | 无 | 无 | 无 |
| 个人中心 | ✓ | ✓ | ✓ | ✓ |

权限模型为「角色 → 权限点」可配置模型，管理员在「系统管理 → 角色管理」中可调整任意角色的权限点。所有权限校验都在服务端执行（`@PreAuthorize` + 数据范围过滤），前端菜单隐藏仅改善体验。

## 数据范围

多角色取最大范围：`ALL` 全部 → `DEPARTMENT` 本部门及子部门 → `PROJECT` 负责的**未关闭**项目组及有效成员 → `SELF` 仅本人。员工列表/详情、部门员工列表、任职记录、绩效、项目组列表/详情/成员均按数据范围过滤（按 ID 直查同样校验，避免越权遍历）。

## 设计假设

1. **四角色 vs 五角色**：早期技术选型文档曾提及"五角色权限控制"，但需求文档只定义了四种角色。本系统只初始化 ADMIN / HR / PROJECT_MANAGER / EMPLOYEE 四种角色，权限模型支持未来新增角色，无需改动代码。
2. **数据库迁移**：采用手动执行 `database/schema.sql` 的方式初始化数据库，未引入 Flyway；演示数据由 `app.seed-demo-data=true` 时的 Java 初始化器在运行时生成（BCrypt 哈希在运行时计算，不落明文）。
3. **退出登录**：系统使用纯无状态 JWT，退出登录为客户端清除 token，已签发的 token 在过期前仍有效（JWT 默认有效期见 `app.jwt.expiration-minutes`）。如需服务端即时撤销，可后续增加 token 黑名单或会话版本字段。不过账号被停用（管理员禁用、离职联动）后，旧 token 会在下一次请求即失效——JWT 过滤器每次都会重新加载账号状态并校验 `status = ACTIVE`。
4. **登录锁定**：连续 5 次密码错误将账号置为 `LOCKED`，需管理员在用户管理中恢复为 `ACTIVE`。失败计数使用单条原子 `UPDATE`（不包事务），因此计数一定会落库；登录成功、失败、被锁定三种结果均写入审计日志并记录来源 IP。
5. **逻辑删除**：核心业务表（用户、角色、部门、员工、项目组）使用 `deleted` 布尔字段软删，物理数据保留；流水/历史表（任职记录、变更申请、审计日志）不做软删。
6. **密码策略**：8-64 位且至少同时包含字母和数字，由后端 `PasswordPolicy` 统一校验（改密、重置、创建用户共用同一入口）；初始密码由 `SecureRandom` 生成 12 位并排除易混淆字符，方便线下口头交付。前端路由守卫与服务端 JWT 过滤器双重拦截（服务端只放行 `/api/v1/profile` 与 `/api/v1/auth`），直接调 API 也无法绕过。用户改密成功后旧 JWT 仍可能在有效期内可用，前端会主动退出登录，服务端即时吊销留待后续增加会话版本字段。
7. **员工姓名**：采用单一 `employee_name` 字段，未拆分姓/名。
8. **状态与组织字段的变更入口**：员工在职状态（试用/转正/调动/离职）只能通过入转调离流程变更，因此未提供通用 `PATCH /api/v1/employees/{id}/status`；部门与岗位同理——档案编辑只在待入职（`PENDING`）阶段能直接修正，入职后改动会被 422 拒绝并提示提交调岗申请。批准申请要求生效日期已到（未到期只能驳回退还改期），且经办人不能办理涉及本人（`req.employeeId` 等于自己 `employeeId`）的申请。员工归档另设 `PATCH /api/v1/employees/{id}/archive`（仅已离职可归档）。在职状态只有 `PENDING`（待入职）/ `PROBATION`（试用期）/ `REGULAR`（正式）/ `RESIGNED`（已离职）四种，**调岗不是状态**：调岗批准只改部门与岗位，试用期调岗仍是试用期（否则永远无法转正）；调岗事实留在任职历史与审计日志里。历史数据里写入过的 `TRANSFERRED` 用 `database/migrations/2026-10-30-remove-transferred-status.sql` 回落。
9. **离职联动**：批准离职的同一事务内自动停用该员工的登录账号、写入项目组成员的 `left_at`、清空指向该员工的部门负责人，审计摘要记录三处影响条数；项目组"负责人"字段暂不强清（表约束要求必填），离职后需人工指定新负责人。
10. **本地配置不入库**：`backend/src/main/resources/application-local.yml` 含本地数据库口令与仅开发用的示例 JWT 密钥，已列入 `.gitignore`，不提交仓库；生产环境一律通过环境变量注入。
11. **审计字段级变更**：员工档案编辑与入转调离批准在写库前对实体取字段快照，写库后比对生成 "字段 旧值→新值" 写入审计摘要（`AuditDiff`）；部门与直属主管显示名称，手机号、邮箱、住址、紧急联系人只记录掩码值（如 `138****1111`），单条超过 500 字截断并标注变更项数。仍写 `summary`（TEXT）而非 JSONB，避免在无 Flyway 的前提下改表。
12. **公司信息配置**：公司名称与 Logo 存在 `sys_config` 的 `company.name`/`company.logo.url`，由管理员在「系统配置」维护；登录页在无 token 阶段就需要展示，因此单独提供 `GET /api/v1/public/company-info` 公开接口（仅返回这两个白名单键，名称缺失回落"公司信息管理系统"）。前端 `stores/company.ts` 统一缓存并同步浏览器标题，配置保存后立即刷新，无需重启。Logo 目前只支持 URL，不做上传。
13. **项目组生命周期**：项目组有 `ACTIVE` / `CLOSED` 两个状态，管理员或项目负责人可通过 `PATCH /api/v1/projects/{id}/status` 关闭或重新开启；关闭后只读（不再允许新增成员），并且从其他人 `PROJECT` 数据范围的可见员工集合中剔除，避免历史成员长期可见。成员关系同时记录 `joined_at` 与 `left_at`，两个日期都可在接口/前端指定（默认当天），用于历史项目补录与"上月退出"这类回溯登记；离开日期早于加入日期会被拒绝。
14. **绩效复核**：评价人提交（`SUBMITTED`）后不能直接改，需人事/管理员用 `POST /api/v1/performance-reviews/{id}/return` 退回（原因必填、记入审计），评价人改稿重新提交后状态回到 `DRAFT`。退回的是"给人改"而不是"作废"，因此没有删除操作。绩效列表与详情统一走 `DataScopeService`：管理员/人事看全部，项目经理看自己评价的记录加范围内被评价人的记录，员工只能看到已提交（非草稿）的本人评价。
15. **申请号与并发**：入转调离申请号是 `CR` + 毫秒时间戳 + 6 位随机数，数据库仍有唯一约束兜底；`create()` 捕获 `DuplicateKeyException` 后重新取号重试（最多 3 次，仍失败返回 409）。该方法故意不加 `@Transactional`——PostgreSQL 里同一事务违反唯一约束后会进入 aborted 状态，同事务重试只会继续失败。
16. **内置角色与角色启停**：ADMIN / HR / PROJECT_MANAGER / EMPLOYEE 四个内置角色的编码被初始化脚本与菜单逻辑依赖，接口拒绝改编码、拒绝停用；ADMIN 的数据范围固定为 `ALL`，且必须保留「角色管理」权限点，否则会出现无人能再维护权限的死锁。自定义角色可在角色管理中启用/停用，停用后其权限点不再生效，相关用户下一次请求即时失去权限。角色的 `data_scope` 若被写入非法枚举值（历史脏数据），按最小权限降级为 `SELF` 并记一条 WARN 日志，不会让接口 500。

17. **菜单即权限点**：侧边栏不再在前端硬编码，改为调用 `GET /api/v1/menus` 下发菜单树，节点来自 `sys_permission` 中 `permission_type = MENU` 的行（`route_path` 为前端真实路由、`icon` 为 Element Plus 图标名、`sort_order` 控制排序、`enabled` 控制上下线）。用户被授予叶子节点即可，父级分组即使没单独授权也会自动带出；父节点被停用或删除时子节点按根节点处理，不会整枝消失。菜单只影响"看得见"，真正的访问控制仍由路由守卫与服务端 `@PreAuthorize` 判定。

## 常见问题

**Q：启动报 `JWT secret 长度不足`？**
A：`JWT_SECRET` 至少 32 字节（HS256 要求），请更换更长的随机字符串。

**Q：登录提示"用户名或密码错误"？**
A：出于安全，用户名不存在与密码错误返回相同提示。确认账号存在且未锁定；连续失败 5 次账号会被锁定，需管理员解锁。

**Q：用户忘记密码或被锁定了怎么办？**
A：管理员在「系统管理 → 用户管理」中点击「重置密码」，留空即生成一次性随机密码并强制该用户下次登录改密；若是被锁定（状态 `LOCKED`），先「启用」恢复为 `ACTIVE` 再重置密码。

**Q：接口返回 403？**
A：当前账号没有对应权限点或超出数据范围，属于预期行为；联系管理员在角色管理中调整。

**Q：如何重置演示数据？**
A：清空数据库后重启（local profile）即可重新初始化：`psql -U postgres -d company_info -f database/schema.sql` 前可先 drop 相关表。

## 部署

生产部署（Nginx 同源部署、环境变量、数据库备份等）见 [docs/deployment.md](docs/deployment.md)。

阿里云轻量应用服务器（Ubuntu 镜像 + PostgreSQL 17）从选镜像到上线的完整流程见 [docs/deployment-alibaba-cloud.md](docs/deployment-alibaba-cloud.md)。


