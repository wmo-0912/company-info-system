# 公司基本信息管理系统：项目实施设计说明书与 Agent 开发任务书

> 依据：`D01 项目需求`、`D02 技术选型方案`。本文将需求文档明确提出的功能作为基线；对文档未规定的实现细节，采用“第一阶段可落地、便于验收”的工程默认值，并标注为设计建议。可将本文件整体交给 Cursor、Claude Code、Codex 等编程 Agent 作为项目实现依据。

---

## 0. Agent 总指令（先阅读）

你是一名资深全栈工程师。请严格按照本说明书实现一个可运行、可联调、可测试的前后端分离“公司基本信息管理系统”。

### 必须遵守的技术栈

- 前端：Vue 3、Vite、TypeScript、Element Plus、Pinia、Vue Router、Axios。
- 后端：Java 17、Spring Boot 3.x、Spring Security、JWT、BCrypt、MyBatis-Plus。
- 数据库：PostgreSQL 17。
- API：RESTful JSON API；使用 springdoc-openapi/Swagger UI 或与 Spring Boot 3 兼容的 Knife4j 版本生成文档。
- 部署：Nginx；开发环境使用 Vite proxy。
- 构建：Maven（后端）、npm（前端）。
- 不要引入 Redis、RabbitMQ、Kafka、微服务、Docker 强制依赖或其他未选定中间件。可以提供可选 Docker Compose，但项目必须能在本机安装 PostgreSQL 后运行。
- 后端采用清晰的 Controller → Service → Mapper 分层，Controller 不写业务逻辑；数据库访问只在 Mapper/数据访问层完成。
- 不要伪造成功结果，不要留空页面或仅有静态演示数据。所有核心页面都必须调用真实后端 API。
- 不要在前端硬编码角色权限作为安全措施；前端权限用于菜单和按钮展示，后端必须独立校验权限和数据范围。
- 所有日期时间、分页、错误响应和字段命名保持统一；关键操作必须有服务端校验。
- 每完成一个阶段，先运行构建和测试，修复错误后再进入下一阶段。
- 先输出当前仓库结构和实施计划；若仓库已有代码，先检查并复用，不得无理由覆盖。若需求有歧义，优先采用本说明书默认值并在 README 的“设计假设”中记录，不要因非关键问题中断整个开发。

### 推荐开发顺序

1. 项目骨架与本地环境。
2. 数据库 schema、基础实体与统一响应。
3. 登录、JWT、Spring Security、角色/权限模型。
4. 部门、员工、数据字典。
5. 入职、转正、调岗、离职流程。
6. 项目组、项目成员、绩效考核。
7. 前端布局、登录、动态菜单与各业务页面。
8. 联调、自动化测试、种子数据、部署文档。

---

## 1. 项目目标与范围

系统名称：公司基本信息管理系统。

系统目标：集中维护组织架构、员工档案、员工入转调离流程、角色和权限、基础数据字典；支持项目经理查看所属项目组员工并进行月度/季度绩效评价；支持普通员工查看个人档案和修改允许自行维护的基本信息。

### 1.1 角色

需求文档明确的业务角色：
- 管理员
- 人事管理
- 项目经理
- 普通员工

技术选型文档提及“五角色权限控制”，但需求文档没有定义第五种角色。**实现时不要擅自创造第五个业务角色**。权限模型应允许未来增加角色；当前种子数据只初始化上述四种角色。README 中记录此差异，若产品方之后明确第五个角色，再补充。

### 1.2 功能模块

1. 登录与账号安全
2. 首页仪表盘
3. 组织架构管理
4. 员工档案管理
5. 入职、转正、调岗、离职流程
6. 项目组与团队管理
7. 月度/季度绩效评价
8. 用户、角色、菜单/操作权限与数据权限管理
9. 系统配置
10. 数据字典
11. 个人中心与电子档案
12. 操作审计日志

操作审计日志属于工程建议，帮助追踪档案和权限变更；需求文档未逐项要求，但涉及员工敏感数据，建议实现。

### 1.3 第一阶段明确不做

- 微服务拆分
- Redis 或消息队列
- 工资核算、考勤、招聘、培训、薪酬发放
- 复杂审批工作流引擎、电子签章
- 文件对象存储服务
- 短信、邮件通知
- AI 功能
- 真实公司单点登录

电子档案第一阶段以系统内员工信息和任职记录为主；若后续需要上传附件，再设计安全文件存储。

---

## 2. 系统架构与目录

### 2.1 逻辑架构

浏览器（Vue SPA）
→ Nginx（生产统一入口；开发时由 Vite proxy 代替）
→ Spring Boot REST API
→ Spring Security + JWT + Permission/Data Scope
→ Service 业务层
→ MyBatis-Plus Mapper
→ PostgreSQL 17

所有接口统一以 `/api/v1` 为前缀。前端不直接访问数据库。生产环境优先采用同源部署：`/` 提供前端静态资源，`/api/` 代理到 Spring Boot。

### 2.2 建议仓库目录

```text
company-info-system/
├─ README.md
├─ .gitignore
├─ docs/
│  ├─ implementation-design.md
│  ├─ api-conventions.md
│  ├─ database-design.md
│  └─ deployment.md
├─ database/
│  ├─ schema.sql
│  └─ seed.sql
├─ backend/
│  ├─ pom.xml
│  └─ src/
│     ├─ main/
│     │  ├─ java/com/example/company/
│     │  │  ├─ CompanyApplication.java
│     │  │  ├─ common/
│     │  │  │  ├─ api/ApiResponse.java
│     │  │  │  ├─ api/PageResponse.java
│     │  │  │  ├─ exception/GlobalExceptionHandler.java
│     │  │  │  ├─ exception/BusinessException.java
│     │  │  │  ├─ security/
│     │  │  │  ├─ audit/
│     │  │  │  └─ util/
│     │  │  ├─ config/
│     │  │  ├─ auth/
│     │  │  ├─ user/
│     │  │  ├─ role/
│     │  │  ├─ department/
│     │  │  ├─ employee/
│     │  │  ├─ employment/
│     │  │  ├─ project/
│     │  │  ├─ performance/
│     │  │  ├─ dictionary/
│     │  │  ├─ system/
│     │  │  └─ audit/
│     │  └─ resources/
│     │     ├─ application.yml
│     │     └─ application-local.yml
│     └─ test/
└─ frontend/
   ├─ package.json
   ├─ vite.config.ts
   ├─ tsconfig.json
   └─ src/
      ├─ api/
      ├─ assets/
      ├─ components/
      ├─ layouts/
      ├─ router/
      ├─ stores/
      ├─ types/
      ├─ utils/
      ├─ views/
      │  ├─ login/
      │  ├─ dashboard/
      │  ├─ organization/
      │  ├─ employees/
      │  ├─ employment/
      │  ├─ projects/
      │  ├─ performance/
      │  ├─ system/
      │  └─ profile/
      └─ main.ts
```

后端每个业务模块按需采用：
- `controller/`：HTTP 参数、校验、返回 DTO。
- `service/`、`service/impl/`：业务逻辑。
- `mapper/`：MyBatis-Plus Mapper。
- `entity/`：数据库实体。
- `dto/`：请求对象。
- `vo/`：响应对象。
- 不要直接把 Entity 暴露给前端；密码、内部字段、敏感信息不能进入通用响应对象。

---

## 3. 环境搭建

以下按 Windows 10/11 + PowerShell 举例。macOS/Linux 可使用对应安装方式。建议开发时使用 IntelliJ IDEA（或兼容 Java 的 IDE）与 VS Code。

### 3.1 必备软件

1. JDK 17（确认 `java -version` 显示 17）。
2. Maven 3.9.x（确认 `mvn -version` 使用 JDK 17）。
3. PostgreSQL 17（本机服务启动，记住数据库用户名和密码）。
4. Node.js LTS（建议 Node 20 或当前与 Vue/Vite 兼容的 LTS；确认 `node -v`、`npm -v`）。
5. Git。
6. IDE：IntelliJ IDEA、VS Code。
7. 可选：Postman/Apifox，用于 API 调试。

用户文档列出了 Python，但当前选定技术栈没有 Python 业务模块，因此 Python 不是系统运行必需项。不要为了“满足安装列表”而额外增加 Python 服务。

### 3.2 检查环境

```powershell
java -version
mvn -version
node -v
npm -v
psql --version
git --version
```

若 `mvn -version` 显示的 Java 不是 17，先修正 `JAVA_HOME` 和 PATH，再继续。

### 3.3 创建 PostgreSQL 数据库

使用 psql 或 pgAdmin 执行：

```sql
CREATE USER company_app WITH PASSWORD '请替换为本地强密码';
CREATE DATABASE company_info OWNER company_app ENCODING 'UTF8';
```

如已使用安装程序创建管理员账户，则以该管理员执行。不要把真实密码提交到 Git。

连接测试：

```powershell
psql -h localhost -p 5432 -U company_app -d company_info
```

### 3.4 后端配置

在 `backend/src/main/resources/application.yml` 中提供安全默认配置；敏感项从环境变量读取：

```yaml
server:
  port: 8080
  servlet:
    context-path: /

spring:
  application:
    name: company-info-system
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/company_info}
    username: ${DB_USERNAME:company_app}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
  jackson:
    time-zone: Asia/Shanghai
    date-format: yyyy-MM-dd HH:mm:ss

mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

app:
  jwt:
    # 必须从环境变量提供高熵密钥；生产环境不得使用示例值
    secret: ${JWT_SECRET}
    expiration-minutes: 120
```

注意：上面的 YAML 是配置模板。Agent 必须确认所用 JWT 库的密钥格式要求，并添加 Bean Validation、CORS/代理策略、日志配置。禁止把真实 JWT secret 写入仓库。

本机 PowerShell 临时设置环境变量示例：

```powershell
$env:DB_PASSWORD="本地数据库密码"
$env:JWT_SECRET="替换成符合所用算法要求的高强度随机密钥"
```

### 3.5 初始化数据库

开发阶段可以执行 `database/schema.sql` 和 `database/seed.sql`。建议使用 Flyway 管理数据库迁移，属于开发工具而非中间件；若采用 Flyway，迁移文件放在 `backend/src/main/resources/db/migration/`，并确保初始化脚本不重复执行造成冲突。二选一并在 README 中写清楚。

### 3.6 启动后端

```powershell
cd backend
mvn clean test
mvn spring-boot:run
```

验证：
- `GET http://localhost:8080/api/v1/health` 返回健康状态。
- Swagger UI 地址按实际依赖配置，例如 `/swagger-ui/index.html`。
- 数据库连接失败时检查 PostgreSQL 服务、端口、库名、用户名、密码。

### 3.7 创建并启动前端

```powershell
cd frontend
npm install
npm run dev
```

`vite.config.ts` 配置代理，将 `/api` 转发到 `http://localhost:8080`。前端通过 `http://localhost:5173` 访问，不在业务代码里硬编码多个环境 URL。

建议 `package.json` 至少包含：

```json
{
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc -b && vite build",
    "preview": "vite preview",
    "type-check": "vue-tsc --noEmit"
  }
}
```

根据实际 Vue/TypeScript 模板校准脚本，不要保留无法运行的命令。

---

## 4. 数据库设计

### 4.1 通用规则

- 主键统一使用 `BIGINT` 自增/Identity。
- 时间字段采用 `TIMESTAMPTZ`，业务日期（如入职日期）采用 `DATE`。
- 每张业务表尽可能包含 `created_at`、`updated_at`；需要软删除的表包含 `deleted BOOLEAN NOT NULL DEFAULT FALSE`。
- 对员工编号、登录用户名、角色编码、部门编码等建立唯一约束。
- 使用外键保证关系完整性；删除有关联数据时默认禁止物理删除，优先停用、归档或软删除。
- 敏感字段最小化返回；身份证号、家庭住址、紧急联系人等不能在列表 API 中无必要地返回。
- 关键业务操作必须在事务中执行。
- 由后端统一填充创建人、更新时间和操作者信息。
- 员工离职后保留历史档案，禁止级联删除员工记录。

### 4.2 核心表

以下是建议的数据模型。Agent 应生成完整 SQL、实体、Mapper 和初始化数据，并确保所有外键和索引可创建。

#### `sys_user`：登录账号
- `id BIGINT PK`
- `username VARCHAR(64) UNIQUE NOT NULL`
- `password_hash VARCHAR(100) NOT NULL`
- `employee_id BIGINT NULL UNIQUE`（关联员工，可空以支持系统管理员）
- `status VARCHAR(20) NOT NULL`：`ACTIVE` / `DISABLED` / `LOCKED`
- `last_login_at TIMESTAMPTZ NULL`
- `failed_login_count INT NOT NULL DEFAULT 0`
- `token_version INT NOT NULL DEFAULT 0` —— 会话版本，改密/重置时 +1，旧 JWT 下一次请求即失效
- `created_at`, `updated_at`, `deleted`

密码必须由 BCrypt 编码，绝不返回密码哈希。

#### `sys_role`：角色
- `id BIGINT PK`
- `role_code VARCHAR(64) UNIQUE NOT NULL`
- `role_name VARCHAR(100) NOT NULL`
- `description TEXT`
- `enabled BOOLEAN NOT NULL DEFAULT TRUE`
- `created_at`, `updated_at`, `deleted`

初始化角色编码建议：`ADMIN`、`HR`、`PROJECT_MANAGER`、`EMPLOYEE`。

#### `sys_user_role`：用户角色关系
- `user_id BIGINT FK`
- `role_id BIGINT FK`
- 复合主键 `(user_id, role_id)`

#### `sys_permission`：权限点
- `id BIGINT PK`
- `permission_code VARCHAR(128) UNIQUE NOT NULL`
- `permission_name VARCHAR(100) NOT NULL`
- `permission_type VARCHAR(20) NOT NULL`：`MENU` / `BUTTON` / `API`
- `parent_id BIGINT NULL`
- `route_path VARCHAR(255) NULL`
- `icon VARCHAR(64) NULL`
- `sort_order INT NOT NULL DEFAULT 0`
- `enabled BOOLEAN NOT NULL DEFAULT TRUE`

#### `sys_role_permission`：角色权限关系
- 复合主键 `(role_id, permission_id)`，外键关联角色与权限。

#### `sys_role_data_scope`（或角色表中的明确字段）：数据权限策略
建议数据范围枚举：
- `ALL`：全公司数据
- `DEPARTMENT`：指定部门及按规则允许的下级部门
- `PROJECT`：本人负责/所属项目的数据
- `SELF`：仅本人数据

数据范围必须在 Service/查询层强制应用，不能只依赖前端传入 `employeeId` 或 `departmentId`。若不同角色在不同模块的数据范围不同，可改为 `sys_role_module_scope(role_id, module_code, scope_type)`。

#### `org_department`：部门
- `id BIGINT PK`
- `department_code VARCHAR(64) UNIQUE NOT NULL`
- `department_name VARCHAR(100) NOT NULL`
- `parent_id BIGINT NULL FK org_department`
- `manager_employee_id BIGINT NULL FK employee`（建表时可先不加 FK，后续迁移添加以避免循环依赖）
- `description TEXT`
- `status VARCHAR(20)`：`ACTIVE` / `DISABLED`
- `created_at`, `updated_at`, `deleted`

必须防止部门形成循环父子关系；不允许删除仍有有效员工或子部门的部门。部门调整要保留历史记录或操作日志。

#### `employee`：员工主档
- `id BIGINT PK`
- `employee_no VARCHAR(32) UNIQUE NOT NULL`
- `first_name VARCHAR(80) NOT NULL`
- `last_name VARCHAR(80) NULL`（如系统只采用中文姓名，可改为 `employee_name` 单字段，统一全项目命名）
- `employee_name VARCHAR(100) NOT NULL`
- `gender VARCHAR(20) NULL`
- `birth_date DATE NULL`
- `phone VARCHAR(32) NULL`
- `email VARCHAR(120) NULL`
- `address VARCHAR(300) NULL`
- `emergency_contact_name VARCHAR(100) NULL`
- `emergency_contact_phone VARCHAR(32) NULL`
- `education_code VARCHAR(64) NULL`
- `department_id BIGINT FK org_department`
- `position_name VARCHAR(100) NULL`
- `position_level_code VARCHAR(64) NULL`
- `manager_employee_id BIGINT NULL FK employee`
- `hire_date DATE NULL`
- `employment_status VARCHAR(20) NOT NULL`：`PENDING` / `PROBATION` / `REGULAR` / `RESIGNED`（调岗不是状态，只改部门与岗位，事件记在任职历史）
- `resignation_date DATE NULL`
- `remark TEXT NULL`
- `created_at`, `updated_at`, `deleted`

实现时不要同时保留无意义的重复姓名字段。Agent 可选用单一 `employee_name`，这是推荐默认值。员工状态与流程状态要区分：员工当前状态是主档状态，流程实例状态保存在流程表。

#### `employee_employment_record`：任职/变更历史
- `id BIGINT PK`
- `employee_id BIGINT FK employee`
- `change_type VARCHAR(20)`：`ONBOARD` / `REGULARIZE` / `TRANSFER` / `RESIGN`
- `effective_date DATE NOT NULL`
- `old_department_id BIGINT NULL`
- `new_department_id BIGINT NULL`
- `old_position_name VARCHAR(100) NULL`
- `new_position_name VARCHAR(100) NULL`
- `reason TEXT NULL`
- `operator_user_id BIGINT FK sys_user`
- `created_at`

用于保留入职、转正、调岗、离职变更历史。

#### `employee_change_request`：入转调离申请/办理记录
- `id BIGINT PK`
- `request_no VARCHAR(40) UNIQUE NOT NULL`
- `employee_id BIGINT FK employee`
- `change_type VARCHAR(20) NOT NULL`
- `status VARCHAR(20) NOT NULL`：`DRAFT` / `SUBMITTED` / `APPROVED` / `REJECTED` / `CANCELLED`
- `effective_date DATE NOT NULL`
- `payload JSONB NOT NULL DEFAULT '{}'`（存储调岗目标部门、岗位、转正日期等经过校验的变更数据；核心字段仍需结构化存储或明确 DTO）
- `reason TEXT`
- `submitted_by BIGINT FK sys_user`
- `processed_by BIGINT NULL FK sys_user`
- `processed_at TIMESTAMPTZ NULL`
- `process_comment TEXT NULL`
- `created_at`, `updated_at`

第一阶段可采用人事直接提交并办理的简化流程，不要求审批引擎。申请批准时必须在一个事务中更新员工主档、插入任职历史、更新申请状态。拒绝不能修改员工主档。状态流转非法时返回业务错误。

#### `sys_dictionary_type`：字典类型
- `id BIGINT PK`
- `dict_code VARCHAR(64) UNIQUE NOT NULL`
- `dict_name VARCHAR(100) NOT NULL`
- `enabled BOOLEAN NOT NULL DEFAULT TRUE`

#### `sys_dictionary_item`：字典项
- `id BIGINT PK`
- `dict_type_id BIGINT FK`
- `item_code VARCHAR(64) NOT NULL`
- `item_label VARCHAR(100) NOT NULL`
- `sort_order INT DEFAULT 0`
- `enabled BOOLEAN DEFAULT TRUE`
- `UNIQUE(dict_type_id, item_code)`

初始化字典至少包括学历、职位层级、性别（若需要）、员工状态显示标签。业务状态代码不要全部做成可随意修改的字典，否则容易破坏业务规则。

#### `project_team`：项目组
- `id BIGINT PK`
- `project_code VARCHAR(64) UNIQUE NOT NULL`
- `project_name VARCHAR(150) NOT NULL`
- `manager_employee_id BIGINT FK employee`
- `description TEXT`
- `status VARCHAR(20)`：`ACTIVE` / `CLOSED`
- `created_at`, `updated_at`, `deleted`

#### `project_team_member`：项目组成员
- `project_team_id BIGINT FK project_team`
- `employee_id BIGINT FK employee`
- `member_role VARCHAR(64) NULL`
- `joined_at DATE NOT NULL`
- `left_at DATE NULL`
- 复合键或唯一约束应防止同一员工重复加入同一项目组且时间区间冲突。

#### `performance_review`：绩效考核
- `id BIGINT PK`
- `employee_id BIGINT FK employee`
- `reviewer_employee_id BIGINT FK employee`
- `project_team_id BIGINT NULL FK project_team`
- `period_type VARCHAR(20)`：`MONTHLY` / `QUARTERLY`
- `period_key VARCHAR(20)`：如 `2026-10` 或 `2026-Q4`
- `score NUMERIC(5,2) NULL`
- `rating VARCHAR(20) NULL`
- `comment TEXT`
- `status VARCHAR(20)`：`DRAFT` / `SUBMITTED` / `RETURNED`
- `submitted_at TIMESTAMPTZ NULL`
- `created_at`, `updated_at`
- 建议唯一约束 `(employee_id, reviewer_employee_id, period_type, period_key)`，具体是否允许多人评价同一员工由业务规则决定。

项目经理只能评价其负责项目组内符合规则的成员；不能通过修改请求中的 reviewer ID 冒充他人。

#### `sys_config`：系统配置
- `id BIGINT PK`
- `config_key VARCHAR(100) UNIQUE NOT NULL`
- `config_value TEXT`
- `description TEXT`
- `updated_by BIGINT FK sys_user`
- `updated_at TIMESTAMPTZ`

仅存储非密钥的系统配置，如公司名称、Logo URL。密钥和密码不能存入普通系统配置。

#### `sys_audit_log`：操作审计
- `id BIGINT PK`
- `actor_user_id BIGINT NULL`
- `action VARCHAR(100) NOT NULL`
- `resource_type VARCHAR(100) NOT NULL`
- `resource_id VARCHAR(100) NULL`
- `result VARCHAR(20) NOT NULL`
- `ip_address VARCHAR(64) NULL`
- `summary TEXT NULL`
- `created_at TIMESTAMPTZ NOT NULL`
- 不记录密码、JWT、身份证号等完整敏感值。对于修改前后内容，使用脱敏摘要或仅记录字段名。

### 4.3 索引建议

至少为以下字段建立索引：
- `employee(employee_no)` 唯一索引
- `employee(department_id, employment_status)`
- `employee(employee_name)`（按需使用 trigram 前需额外扩展；第一阶段可用普通索引）
- `employee_change_request(status, created_at)`
- `employee_change_request(employee_id, change_type)`
- `employee_employment_record(employee_id, effective_date DESC)`
- `project_team(manager_employee_id, status)`
- `project_team_member(employee_id, left_at)`
- `performance_review(employee_id, period_type, period_key)`
- `sys_audit_log(actor_user_id, created_at DESC)`

---

## 5. 权限与安全设计

### 5.1 认证

- 使用 Spring Security + JWT。
- 登录成功后返回短期访问 Token；第一阶段可只使用单一 access token。若实现刷新 Token，必须设计可撤销策略，不能简单无限期签发。
- BCrypt 存储密码，禁止明文或可逆加密存储。
- 登录失败返回统一错误，不透露用户名是否存在。
- 对连续失败登录进行简单限流/锁定策略；无需 Redis，可用数据库字段实现基础锁定，注意并发更新。
- JWT secret 通过环境变量注入；生产使用 HTTPS。
- 退出登录：如果系统采用纯无状态 JWT，前端清除 token 只能实现客户端退出，无法即时使已签发 token 失效。若需要服务端即时注销，可增加数据库 token denylist/会话版本字段；不要声称纯 JWT 已实现服务端撤销。

### 5.2 权限点编码建议

- `dashboard:view`
- `department:read`, `department:create`, `department:update`, `department:disable`
- `employee:read`, `employee:create`, `employee:update`, `employee:archive`
- `employment:submit`, `employment:process`
- `project:read`, `project:create`, `project:update`, `project:member:manage`
- `performance:read`, `performance:create`, `performance:update`, `performance:submit`
- `user:read`, `user:create`, `user:update`, `user:disable`
- `role:read`, `role:manage`
- `permission:manage`
- `dictionary:read`, `dictionary:manage`
- `system:config:read`, `system:config:update`
- `profile:read`, `profile:update`
- `audit:read`

### 5.3 角色默认权限建议

| 模块 | 管理员 | 人事 | 项目经理 | 普通员工 |
|---|---|---|---|---|
| 系统配置/用户/角色/权限 | 管理 | 无 | 无 | 无 |
| 数据字典 | 管理 | 查看 | 查看 | 查看必要字典 |
| 部门组织架构 | 全部管理 | 查看/按授权维护 | 查看相关部门 | 无或只读组织摘要 |
| 员工档案 | 全部（建议默认只读敏感字段，按授权操作） | 全公司维护 | 仅负责项目成员必要字段 | 仅本人 |
| 入转调离 | 按授权管理 | 办理 | 仅查看必要的团队变化信息 | 查看本人相关记录 |
| 项目组 | 全局管理（如授权） | 查看 | 管理自己负责的项目组 | 查看与自己相关的信息 |
| 绩效 | 全局审计/查看（按授权） | 按授权查看 | 对所属项目组成员评价 | 查看本人已发布评价 |
| 个人中心 | 可查看自己 | 可查看自己 | 可查看自己 | 可查看自己 |
| 操作日志 | 管理/审计权限控制 | 默认无 | 无 | 无 |

注意：这是可执行的默认权限矩阵，不是需求文件中逐项写定的规则。所有权限都应可配置。管理员不应因角色名而绕过所有服务端数据校验；高权限操作应写审计日志。

### 5.4 数据范围实现要求

- `ALL`：允许查询所有范围内数据。
- `DEPARTMENT`：当前用户授权部门及其子部门（若启用树状部门）。
- `PROJECT`：只允许查询其负责项目组或有效成员范围内的数据。
- `SELF`：只允许查询与当前用户绑定的 employee ID。
- 服务端从 SecurityContext 取得当前用户和角色；不得信任请求参数中的 `userId`、`reviewerId` 来确定身份。
- 员工列表、详情、导出（若后续实现）、绩效、任职记录均须执行数据范围检查。
- 不允许仅靠 Vue 路由守卫实现权限控制。

### 5.5 敏感数据

- 普通列表只返回姓名、员工编号、部门、岗位、状态等必要字段。
- 详细联系方式只对本人、人事及授权角色开放。
- 普通员工更新个人信息时，只允许修改手机号、住址等白名单字段；不能自行改员工编号、部门、职位、状态、入职/离职日期、角色。
- 生产日志禁止打印 Authorization Header、JWT、密码或完整紧急联系人信息。
- 所有输入在后端校验；前端校验只用于改善体验。

---

## 6. API 统一规范

### 6.1 路由与响应

统一前缀：`/api/v1`。

成功响应建议：

```json
{
  "code": "OK",
  "message": "success",
  "data": {},
  "timestamp": "2026-10-29T14:00:00+08:00"
}
```

分页响应建议：

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "records": [],
    "page": 1,
    "pageSize": 10,
    "total": 0
  },
  "timestamp": "2026-10-29T14:00:00+08:00"
}
```

错误响应也使用统一结构，例如：
- `400` 参数错误
- `401` 未认证或 token 无效
- `403` 无权限
- `404` 资源不存在
- `409` 状态冲突、重复编号、重复用户名
- `422` 业务规则不满足（如不合法的状态流转）
- `500` 未预期错误，不暴露堆栈

分页参数：`page` 从 1 开始，`pageSize` 默认 10，最大 100。排序字段必须白名单校验，防止 SQL 注入。

### 6.2 认证接口

- `POST /api/v1/auth/login`
  - 请求：`{ "username": "...", "password": "..." }`
  - 返回：`{ token, tokenType: "Bearer", expiresAt, user: { id, username, displayName, roles, permissions } }`
- `GET /api/v1/auth/me`：当前用户资料、角色和权限。
- `POST /api/v1/auth/logout`：清理客户端 token；若无服务端撤销实现，响应说明不代表 token 即时失效。

### 6.3 首页接口

- `GET /api/v1/dashboard/summary`
  - 按当前用户权限返回可见范围内的员工总数、试用期人数、近期入职人数、待处理变更数、我的项目组数、待完成绩效数。
  - 每个数字必须按权限范围查询，不可向普通员工泄漏全公司统计数据，除非产品明确允许。

### 6.4 部门接口

- `GET /api/v1/departments/tree`
- `GET /api/v1/departments`（分页/筛选）
- `GET /api/v1/departments/{id}`
- `POST /api/v1/departments`
- `PUT /api/v1/departments/{id}`
- `PATCH /api/v1/departments/{id}/status`
- `GET /api/v1/departments/{id}/employees`

请求字段包含 `departmentCode`、`departmentName`、`parentId`、`managerEmployeeId`、`description`、`status`。禁止创建循环树；停用有有效员工或子部门的部门时返回 409/422。

### 6.5 员工接口

- `GET /api/v1/employees?page=1&pageSize=10&keyword=&departmentId=&status=`
- `GET /api/v1/employees/{id}`
- `POST /api/v1/employees`
- `PUT /api/v1/employees/{id}`
- `PATCH /api/v1/employees/{id}/status`
- `GET /api/v1/employees/{id}/employment-records`
- `GET /api/v1/employees/{id}/change-requests`

员工创建时校验员工编号唯一、部门有效、主管关系合法。编辑员工资料必须区分可修改字段和受流程控制的字段。不能通过通用 `PUT` 直接把员工改成离职或转正，必须通过对应流程接口。

### 6.6 入转调离接口

- `GET /api/v1/employment/requests`
- `GET /api/v1/employment/requests/{id}`
- `POST /api/v1/employment/requests`
- `POST /api/v1/employment/requests/{id}/submit`
- `POST /api/v1/employment/requests/{id}/process`
- `POST /api/v1/employment/requests/{id}/cancel`

创建请求示例：

```json
{
  "employeeId": 1001,
  "changeType": "TRANSFER",
  "effectiveDate": "2026-11-01",
  "reason": "组织调整",
  "payload": {
    "targetDepartmentId": 20,
    "targetPositionName": "后端工程师"
  }
}
```

办理接口示例：

```json
{
  "decision": "APPROVE",
  "comment": "资料核验完成"
}
```

规则：
- 只有具备办理权限的人事/授权管理员可以办理。
- 只有 `SUBMITTED` 状态能办理；已办理请求不可重复办理。
- 批准时事务性更新员工状态/部门/岗位、写入 `employee_employment_record`、更新请求状态。
- 转正只允许符合当前员工状态的请求。
- 调岗必须校验目标部门有效。
- 离职要校验日期及当前状态，保存离职日期但不删除员工。
- 所有状态流转必须有单元测试。

### 6.7 项目组接口

- `GET /api/v1/projects`
- `GET /api/v1/projects/{id}`
- `POST /api/v1/projects`
- `PUT /api/v1/projects/{id}`
- `GET /api/v1/projects/{id}/members`
- `POST /api/v1/projects/{id}/members`
- `DELETE /api/v1/projects/{id}/members/{employeeId}`（建议逻辑结束成员关系，写入 `left_at`，不要物理删除历史）
- `GET /api/v1/projects/my-teams`

项目经理只能维护其负责且有权限的项目组；其他角色根据权限矩阵授权。

### 6.8 绩效接口

- `GET /api/v1/performance-reviews`
- `GET /api/v1/performance-reviews/{id}`
- `POST /api/v1/performance-reviews`
- `PUT /api/v1/performance-reviews/{id}`
- `POST /api/v1/performance-reviews/{id}/submit`
- `GET /api/v1/performance-reviews/my`

创建/更新字段：`employeeId`、`projectTeamId`、`periodType`、`periodKey`、`score`、`rating`、`comment`。后端校验：
- 评价人来自当前登录用户对应的员工档案，不接受客户端指定为他人。
- 被评价人必须在授权项目组中。
- `score` 范围建议 0–100，允许空值仅在草稿状态。
- 月度周期格式 `YYYY-MM`；季度格式 `YYYY-Q1` 至 `YYYY-Q4`。
- 已提交评价不可随意修改；如需修改，走退回或授权更正流程。
- 普通员工只能查看本人允许查看的绩效结果，不应看到其他员工评价。

### 6.9 用户与角色接口

- `GET /api/v1/users`
- `GET /api/v1/users/{id}`
- `POST /api/v1/users`
- `PUT /api/v1/users/{id}`
- `PATCH /api/v1/users/{id}/status`
- `PUT /api/v1/users/{id}/roles`
- `GET /api/v1/roles`
- `POST /api/v1/roles`
- `PUT /api/v1/roles/{id}`
- `PUT /api/v1/roles/{id}/permissions`
- `GET /api/v1/permissions/tree`

创建用户时只存 BCrypt hash；可由管理员设置初始密码或采用随机临时密码方案，但不得在日志中打印。若系统没有邮件/短信服务，README 需说明初始密码交付方式，不要假装已发送通知。

### 6.10 字典与系统配置接口

- `GET /api/v1/dictionaries/{dictCode}/items`
- `GET /api/v1/dictionaries`
- `POST /api/v1/dictionaries`
- `POST /api/v1/dictionaries/{dictCode}/items`
- `PUT /api/v1/dictionary-items/{id}`
- `PATCH /api/v1/dictionary-items/{id}/status`
- `GET /api/v1/system/config`
- `PUT /api/v1/system/config`

仅管理员可修改字典和系统配置。字典项被业务数据引用后，优先停用，不直接删除。

### 6.11 个人中心与审计接口

- `GET /api/v1/profile`
- `PUT /api/v1/profile`（仅允许修改白名单字段）
- `GET /api/v1/profile/employment-records`
- `GET /api/v1/audit-logs`（需 `audit:read` 权限）

---

## 7. 后端实现要求

### 7.1 依赖建议

使用 Spring Boot 3.x BOM 管理版本，至少包括：
- `spring-boot-starter-web`
- `spring-boot-starter-validation`
- `spring-boot-starter-security`
- `spring-boot-starter-aop`（若用审计切面）
- `mybatis-plus-spring-boot3-starter`（选择与 Spring Boot 3 兼容的版本）
- PostgreSQL JDBC driver
- JWT 库（选择维护活跃、与 Java 17 兼容的库，并锁定版本）
- `springdoc-openapi-starter-webmvc-ui` 或兼容的 Knife4j 方案，二者不要重复引入冲突版本
- `flyway-core` 及对应 PostgreSQL 支持依赖（若采用 Flyway）
- `spring-boot-starter-test`
- Spring Security Test

### 7.2 统一代码规范

- DTO 使用 Jakarta Validation：`@NotBlank`、`@Size`、`@Email`、`@Positive` 等。
- Service 负责业务规则和事务，写操作按需使用 `@Transactional`。
- Controller 仅接收 DTO、调用 Service、返回统一响应。
- 统一异常处理，业务错误使用明确错误码。
- MyBatis-Plus 分页插件必须注册，并限定最大页大小。
- 不要把动态 SQL 排序字段直接拼接用户输入。
- 使用枚举处理员工状态、申请类型、申请状态、绩效周期和角色范围。
- 防止越权 IDOR：查询、更新、删除每个资源时均检查当前用户对该资源的权限和数据范围。
- 不要用 `@CrossOrigin("*")` 作为生产默认配置；开发代理下可不开放跨域，生产优先同源。

### 7.3 测试要求

至少覆盖：
- 登录成功/密码错误/禁用账号/无 token/过期或错误 token。
- 普通员工无法读取其他员工档案。
- 项目经理不能读取非所属项目组的完整员工档案。
- 人事能办理入转调离。
- 非法状态流转失败。
- 调岗批准会同时更新员工部门并新增历史记录。
- 重复员工编号/用户名被拒绝。
- 部门不能形成循环。
- 普通员工不能修改部门、岗位、角色和员工状态。
- 项目经理不能冒充他人提交绩效。
- 分页边界和最大 pageSize。
- 软删除/停用对象不能被误当作有效对象使用。

---

## 8. 前端实现要求

### 8.1 页面框架

使用 Element Plus 构建后台管理布局：
- 左侧菜单栏
- 顶部导航栏（当前用户、角色、退出登录）
- 面包屑
- 主内容区域
- 统一加载、空状态、错误状态
- 表格分页、筛选、重置、详情抽屉/弹窗
- 表单必填和格式校验

视觉风格：清晰、克制、企业后台风格；统一间距、标题、按钮、状态标签；避免所有内容挤在一个页面。所有关键页面需适配常见桌面分辨率。

### 8.2 路由

建议路由：
- `/login`
- `/dashboard`
- `/organization/departments`
- `/employees`
- `/employees/:id`
- `/employment/requests`
- `/projects`
- `/projects/:id`
- `/performance`
- `/system/users`
- `/system/roles`
- `/system/dictionaries`
- `/system/config`
- `/system/audit-logs`
- `/profile`

使用 Vue Router 路由守卫检查登录状态，并从 `/auth/me` 获取权限。动态菜单由后端权限或统一前端权限配置映射生成。前端路由守卫只负责导航体验，后端鉴权才是安全边界。

### 8.3 Pinia

至少创建：
- `useAuthStore`：token、当前用户、角色、权限、登录/退出、加载当前用户。
- 如确有需要，再创建字典缓存 store；第一阶段避免过度拆分状态。

Token 存储策略必须明确。简单教学/内网版本可存 sessionStorage，降低浏览器关闭后的残留时间；若采用 localStorage，需说明 XSS 风险并做好输入输出安全。更高安全方案可采用 HttpOnly Secure Cookie，但需相应设计 CSRF 防护和后端 Cookie 机制，不能只改前端存储位置。

### 8.4 Axios

统一配置：
- `baseURL: '/api/v1'`
- 请求拦截器添加 `Authorization: Bearer <token>`
- 响应拦截器解析统一响应体
- `401` 清除会话并跳转登录
- `403` 显示无权限提示
- 业务错误显示后端 message
- 防止同一 401 触发大量重复跳转
- 不要将 token、密码输出到控制台

每个业务模块建立独立 API 文件，如 `api/employees.ts`，页面不得直接散落 Axios 请求。

### 8.5 必须实现的页面

1. 登录页：用户名、密码、错误提示、加载状态。
2. 首页：展示当前用户有权限查看的统计卡片和常用入口。
3. 部门管理：树形结构、创建/编辑、父部门选择、状态维护、关联员工查看。
4. 员工管理：分页、关键词、部门、状态筛选；创建、编辑、详情；按权限显示敏感信息。
5. 员工详情：基础信息、任职历史、入转调离记录。
6. 入转调离：列表、条件筛选、创建申请、详情、办理/批准/拒绝、状态标签。
7. 项目组：项目列表、详情、成员维护、我的项目组。
8. 绩效：周期筛选、创建/编辑草稿、提交、本人评价查看。
9. 用户管理：账号列表、创建账号、启停、角色分配。
10. 角色与权限：角色列表、权限树勾选、保存。
11. 字典管理：字典类型、字典项新增/编辑/停用。
12. 系统配置：公司名称、Logo 等非密钥配置。
13. 个人中心：查看个人资料、修改允许修改的字段、查看任职历史。
14. 审计日志：按操作人、资源类型、日期筛选（需授权）。

所有页面都应有真实 API 调用、加载状态、错误提示、空数据状态和基本表单校验。

---

## 9. 初始化演示数据

提供 `seed.sql` 或开发环境初始化器，至少包含：
- 1 个管理员账号
- 1 个人事账号
- 1 个项目经理账号
- 2–3 个普通员工账号
- 3–5 个部门
- 2 个项目组及成员关系
- 学历和职位层级字典
- 示例入职、转正、调岗记录
- 示例月度/季度绩效数据
- 默认角色、权限点和角色权限关联

所有演示账号密码都必须在 README 中明确标为开发环境专用，数据库只存 BCrypt hash。建议提供一次性初始化命令或生成脚本来生成 hash，不要把可用于生产的默认密码硬编码到生产配置。生产启动时不得自动创建公开默认管理员。

---

## 10. Nginx 部署

生产建议同源部署：
- `/` 指向前端 `dist` 静态文件。
- `/api/` 代理至 `http://127.0.0.1:8080/api/`。
- 配置 SPA fallback 到 `index.html`。
- 配置合理的请求体大小和超时。
- 生产启用 HTTPS，JWT secret 与数据库密码使用环境变量或安全配置注入。
- PostgreSQL 不直接暴露公网。
- 日志避免敏感信息。

部署文档必须包括：
1. 构建前端：`npm ci && npm run build`
2. 后端打包：`mvn clean package`
3. 配置环境变量
4. 初始化/迁移数据库
5. 启动 Spring Boot jar
6. 配置 Nginx
7. 健康检查和日志排查
8. 数据库备份与恢复的基本说明

---

## 11. 验收标准

只有以下条件全部满足，才可称为第一阶段完成：

### 构建与启动
- 后端 `mvn clean test` 成功。
- 前端 `npm run type-check`、`npm run build` 成功。
- PostgreSQL 初始化脚本能从空数据库执行。
- 按 README 步骤可从零启动前后端。

### 功能
- 登录、退出、禁用账号拦截正常。
- 四种角色看到的菜单和可执行操作不同。
- 后端 API 对无权限请求返回 401/403，而不是只在前端隐藏按钮。
- 部门增改查、树形展示与循环校验正常。
- 员工增改查、分页、筛选正常。
- 入转调离状态流转正确且保留历史。
- 项目经理只能查看授权项目组及成员。
- 绩效评价能保存、提交，并限制查看范围。
- 管理员可以维护角色、权限、账号、字典和系统配置。
- 普通员工只能修改个人资料白名单字段。
- 审计日志不泄露敏感字段。

### 质量
- 无关键页面仅使用硬编码假数据。
- 无密码明文存储、无 JWT secret 提交到 Git。
- 无已知关键权限绕过问题。
- 核心业务规则有自动化测试。
- API 文档可访问。
- README 包含环境要求、启动命令、账号、权限矩阵、配置项和常见问题。

---

## 12. 分阶段给 Agent 的执行提示词

请按阶段执行，不要一次性生成所有代码后不验证。

### 阶段 A：检查与搭建骨架
“阅读 `docs/implementation-design.md`。先检查仓库现状，输出拟创建/修改的文件列表。搭建 Spring Boot 3 + Java 17 + Maven 后端骨架和 Vue 3 + Vite + TypeScript + Element Plus 前端骨架，配置 PostgreSQL、MyBatis-Plus、统一响应、统一异常处理、分页插件、OpenAPI、Vite `/api` 代理。添加健康检查接口和 README。运行后端测试、前端类型检查与构建，并修复所有错误。”

### 阶段 B：数据库与安全
“实现 schema/migration、实体、Mapper、初始化角色和权限、统一 JWT 认证、BCrypt、Spring Security、当前用户上下文、权限校验、数据范围服务、登录和 `/auth/me`。先编写安全测试，证明普通员工不能读取其他员工数据。禁止只做前端权限。”

### 阶段 C：组织与员工
“实现部门和员工后端 CRUD、分页筛选、DTO 校验、数据范围、软删除/停用规则；实现对应 Vue 页面并接入真实 API。部门树必须防循环；员工通用编辑不能直接改变由入转调离流程控制的状态。”

### 阶段 D：入转调离
“实现入职、转正、调岗、离职请求及状态机。批准时使用事务更新员工主档、写入任职历史、更新请求状态。为每一种合法/非法状态转换编写测试，前端实现列表、详情、提交、办理、拒绝和取消。”

### 阶段 E：项目组与绩效
“实现项目组、成员关系和月度/季度绩效。项目经理只能操作自己负责的项目组，评价人必须从当前登录用户推导，不能信任客户端 reviewerId。实现前端列表、编辑、提交、本人评价查看，并补充越权测试。”

### 阶段 F：系统管理与个人中心
“实现用户账号、角色、权限树、数据字典、系统配置、个人中心、审计日志。普通员工更新个人资料只能更新白名单字段。关键操作写审计日志，但不得记录密码、JWT 或完整敏感字段。”

### 阶段 G：联调与交付
“从空数据库执行迁移与 seed；启动后端和前端完成端到端冒烟测试；运行全部单元/集成测试、前端类型检查和生产构建。修复问题后补齐 Swagger/OpenAPI、README、Nginx 配置示例、部署文档、演示账号和已知限制。最终报告：实现模块、关键文件、运行命令、测试结果、未完成事项和风险，不得声称未运行的测试已通过。”

---

## 13. 重要设计假设与待确认项

1. 需求文件定义了四种具体角色；技术方案提到“五角色”，第五种角色尚未定义，因此本方案不擅自新增。
2. 入转调离采用人事办理的简化状态流转，不引入工作流引擎。
3. 项目经理与项目组通过 `project_team.manager_employee_id` 和成员表关联。
4. 第一阶段不实现附件上传；“电子档案”先展示员工主档与任职历史。
5. 员工字段按通用企业档案设计；正式上线前应由业务方确认必填字段、敏感字段可见范围及保留期限。
6. 绩效评分建议 0–100，月度/季度周期分别使用 `YYYY-MM`、`YYYY-Qn`；若单位有既定评分体系应替换。
7. 文档提及系统配置包括公司名称、Logo；Logo 第一阶段可使用 URL，不提供上传服务。
8. 文档未规定多公司/租户，因此当前按单公司系统设计。
9. 文档未明确员工档案导入导出，第一阶段不把 Excel 导入导出列为必做项。
10. 数据库迁移可用 Flyway；若 Agent 选择直接 SQL 初始化，必须保证步骤可重复、文档完整，不得两套机制混用。

**交付要求：** 最终交付可运行源码、SQL/迁移脚本、演示数据、API 文档、环境配置示例、测试、README 与部署文档。任何未实现的功能都必须明确列出，不能用静态页面冒充完成。
