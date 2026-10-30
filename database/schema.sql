-- 公司基本信息管理系统 数据库结构（PostgreSQL 17）
-- 从空数据库执行：psql -h localhost -U company_app -d company_info -f schema.sql

BEGIN;

-- ============ 系统域 ============

CREATE TABLE sys_user (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username           VARCHAR(64)  NOT NULL UNIQUE,
    password_hash      VARCHAR(100) NOT NULL,
    employee_id        BIGINT       NULL UNIQUE,
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / DISABLED / LOCKED
    must_change_password BOOLEAN    NOT NULL DEFAULT FALSE, -- 首次登录/管理员重置后强制改密
    last_login_at      TIMESTAMPTZ  NULL,
    failed_login_count INT          NOT NULL DEFAULT 0,
    token_version      INT          NOT NULL DEFAULT 0, -- 会话版本：改密/重置/禁用时 +1，旧 token 立即失效
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted            BOOLEAN      NOT NULL DEFAULT FALSE
);

-- 历史密码指纹：改密/重置时写入，仅保留最近 5 条，用于阻止改回旧密码
CREATE TABLE sys_password_history (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES sys_user (id),
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_password_history_user ON sys_password_history (user_id, id DESC);

CREATE TABLE sys_role (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    role_code   VARCHAR(64)  NOT NULL UNIQUE,
    role_name   VARCHAR(100) NOT NULL,
    description TEXT         NULL,
    data_scope  VARCHAR(20)  NOT NULL DEFAULT 'SELF', -- ALL / DEPARTMENT / PROJECT / SELF
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL REFERENCES sys_user (id),
    role_id BIGINT NOT NULL REFERENCES sys_role (id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE sys_permission (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    permission_code VARCHAR(128) NOT NULL UNIQUE,
    permission_name VARCHAR(100) NOT NULL,
    permission_type VARCHAR(20)  NOT NULL, -- MENU / BUTTON / API
    parent_id       BIGINT       NULL,
    route_path      VARCHAR(255) NULL,
    icon            VARCHAR(64)  NULL,
    sort_order      INT          NOT NULL DEFAULT 0,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE sys_role_permission (
    role_id       BIGINT NOT NULL REFERENCES sys_role (id),
    permission_id BIGINT NOT NULL REFERENCES sys_permission (id),
    PRIMARY KEY (role_id, permission_id)
);

-- 角色在单个模块上的数据范围覆盖（未配置的模块沿用 sys_role.data_scope）
CREATE TABLE sys_role_module_scope (
    role_id     BIGINT      NOT NULL REFERENCES sys_role (id) ON DELETE CASCADE,
    module_code VARCHAR(32) NOT NULL, -- DataModule 枚举：EMPLOYEE/DEPARTMENT/PROJECT/PERFORMANCE/EMPLOYMENT
    scope_type  VARCHAR(20) NOT NULL, -- ALL/DEPARTMENT/PROJECT/SELF
    PRIMARY KEY (role_id, module_code)
);


CREATE TABLE sys_config (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    config_key   VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT         NULL,
    description  TEXT         NULL,
    updated_by   BIGINT       NULL REFERENCES sys_user (id),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE sys_audit_log (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_user_id BIGINT       NULL,
    actor_name    VARCHAR(64)  NULL,
    action        VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id   VARCHAR(100) NULL,
    result        VARCHAR(20)  NOT NULL,
    ip_address    VARCHAR(64)  NULL,
    summary       TEXT         NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_actor_created ON sys_audit_log (actor_user_id, created_at DESC);
CREATE INDEX idx_audit_resource ON sys_audit_log (resource_type, resource_id);

-- ============ 组织域 ============

CREATE TABLE org_department (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    department_code     VARCHAR(64)  NOT NULL UNIQUE,
    department_name     VARCHAR(100) NOT NULL,
    parent_id           BIGINT       NULL REFERENCES org_department (id),
    manager_employee_id BIGINT       NULL, -- FK 在 employee 建表后补充，避免循环依赖
    description         TEXT         NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / DISABLED
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE employee (
    id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_no              VARCHAR(32)  NOT NULL UNIQUE,
    employee_name            VARCHAR(100) NOT NULL,
    gender                   VARCHAR(20)  NULL,
    birth_date               DATE         NULL,
    phone                    VARCHAR(32)  NULL,
    email                    VARCHAR(120) NULL,
    address                  VARCHAR(300) NULL,
    emergency_contact_name   VARCHAR(100) NULL,
    emergency_contact_phone  VARCHAR(32)  NULL,
    education_code           VARCHAR(64)  NULL,
    department_id            BIGINT       NOT NULL REFERENCES org_department (id),
    position_name            VARCHAR(100) NULL,
    position_level_code      VARCHAR(64)  NULL,
    manager_employee_id      BIGINT       NULL REFERENCES employee (id),
    hire_date                DATE         NULL,
    employment_status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING/PROBATION/REGULAR/RESIGNED（调岗不改变状态）
    resignation_date         DATE         NULL,
    remark                   TEXT         NULL,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted                  BOOLEAN      NOT NULL DEFAULT FALSE
);

ALTER TABLE org_department
    ADD CONSTRAINT fk_department_manager FOREIGN KEY (manager_employee_id) REFERENCES employee (id);
ALTER TABLE sys_user
    ADD CONSTRAINT fk_user_employee FOREIGN KEY (employee_id) REFERENCES employee (id);

CREATE UNIQUE INDEX idx_employee_no ON employee (employee_no);
CREATE INDEX idx_employee_dept_status ON employee (department_id, employment_status);
CREATE INDEX idx_employee_name ON employee (employee_name);

CREATE TABLE employee_employment_record (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id        BIGINT       NOT NULL REFERENCES employee (id),
    change_type        VARCHAR(20)  NOT NULL, -- ONBOARD / REGULARIZE / TRANSFER / RESIGN
    effective_date     DATE         NOT NULL,
    old_department_id  BIGINT       NULL,
    new_department_id  BIGINT       NULL,
    old_position_name  VARCHAR(100) NULL,
    new_position_name  VARCHAR(100) NULL,
    reason             TEXT         NULL,
    operator_user_id   BIGINT       NULL REFERENCES sys_user (id),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_record_employee_date ON employee_employment_record (employee_id, effective_date DESC);

CREATE TABLE employee_change_request (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    request_no      VARCHAR(40)  NOT NULL UNIQUE,
    employee_id     BIGINT       NULL REFERENCES employee (id), -- 入职申请可先不带员工，批准时才建档
    change_type     VARCHAR(20)  NOT NULL, -- ONBOARD / REGULARIZE / TRANSFER / RESIGN
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT', -- DRAFT/SUBMITTED/APPROVED/REJECTED/CANCELLED
    effective_date  DATE         NOT NULL,
    payload         JSONB        NOT NULL DEFAULT '{}',
    reason          TEXT         NULL,
    submitted_by    BIGINT       NOT NULL REFERENCES sys_user (id),
    processed_by    BIGINT       NULL REFERENCES sys_user (id),
    processed_at    TIMESTAMPTZ  NULL,
    process_comment TEXT         NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_change_req_status_created ON employee_change_request (status, created_at);
CREATE INDEX idx_change_req_employee ON employee_change_request (employee_id, change_type);

-- ============ 字典 ============

CREATE TABLE sys_dictionary_type (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dict_code VARCHAR(64)  NOT NULL UNIQUE,
    dict_name VARCHAR(100) NOT NULL,
    enabled   BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE sys_dictionary_item (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dict_type_id BIGINT       NOT NULL REFERENCES sys_dictionary_type (id),
    item_code    VARCHAR(64)  NOT NULL,
    item_label   VARCHAR(100) NOT NULL,
    sort_order   INT          NOT NULL DEFAULT 0,
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    UNIQUE (dict_type_id, item_code)
);

-- ============ 项目与绩效 ============

CREATE TABLE project_team (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_code        VARCHAR(64)  NOT NULL UNIQUE,
    project_name        VARCHAR(150) NOT NULL,
    manager_employee_id BIGINT       NOT NULL REFERENCES employee (id),
    description         TEXT         NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / CLOSED
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_project_manager_status ON project_team (manager_employee_id, status);

CREATE TABLE project_team_member (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_team_id BIGINT      NOT NULL REFERENCES project_team (id),
    employee_id     BIGINT      NOT NULL REFERENCES employee (id),
    member_role     VARCHAR(64) NULL,
    joined_at       DATE        NOT NULL,
    left_at         DATE        NULL
);

-- 同一员工在同一项目组仅允许一条有效（未离开）成员记录
CREATE UNIQUE INDEX uq_project_member_active ON project_team_member (project_team_id, employee_id) WHERE left_at IS NULL;
CREATE INDEX idx_project_member_employee ON project_team_member (employee_id, left_at);

CREATE TABLE performance_review (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id          BIGINT        NOT NULL REFERENCES employee (id),
    reviewer_employee_id BIGINT        NOT NULL REFERENCES employee (id),
    project_team_id      BIGINT        NULL REFERENCES project_team (id),
    period_type          VARCHAR(20)   NOT NULL, -- MONTHLY / QUARTERLY
    period_key           VARCHAR(20)   NOT NULL, -- YYYY-MM / YYYY-Qn
    score                NUMERIC(5, 2) NULL,
    rating               VARCHAR(20)   NULL,
    comment              TEXT          NULL,
    status               VARCHAR(20)   NOT NULL DEFAULT 'DRAFT', -- DRAFT / SUBMITTED / RETURNED
    submitted_at         TIMESTAMPTZ   NULL,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (employee_id, reviewer_employee_id, period_type, period_key)
);

-- 绩效周期锁定：只登记被显式锁定/解锁过的周期，未登记的周期视为未锁定
CREATE TABLE performance_period (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    period_key VARCHAR(20) NOT NULL UNIQUE, -- 例如 2026-09 / 2026-Q3，与 performance_review.period_key 对应
    locked     BOOLEAN     NOT NULL DEFAULT FALSE,
    locked_at  TIMESTAMPTZ NULL,
    locked_by  BIGINT      NULL REFERENCES sys_user (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);


CREATE INDEX idx_review_employee_period ON performance_review (employee_id, period_type, period_key);

COMMIT;
