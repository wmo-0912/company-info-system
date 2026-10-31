-- 绩效周期锁定：锁定后该周期的评价不可新增/修改/提交
-- 已上线的库执行本脚本；空库直接跑 database/schema.sql 即可（已包含该表）

BEGIN;

CREATE TABLE IF NOT EXISTS performance_period (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    period_key VARCHAR(20) NOT NULL UNIQUE,
    locked     BOOLEAN     NOT NULL DEFAULT FALSE,
    locked_at  TIMESTAMPTZ NULL,
    locked_by  BIGINT      NULL REFERENCES sys_user (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE performance_period IS '绩效周期锁定状态（未登记的周期视为未锁定）';

COMMIT;
