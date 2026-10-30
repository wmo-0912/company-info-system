-- 密码历史：阻止用户在最近 5 个旧密码之间来回切换
-- 已上线的库执行本脚本；空库直接跑 database/schema.sql 即可（已包含该表）

BEGIN;

CREATE TABLE IF NOT EXISTS sys_password_history (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES sys_user (id),
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_password_history_user ON sys_password_history (user_id, id DESC);

COMMENT ON TABLE sys_password_history IS '历史密码指纹（只存 BCrypt 哈希，保留最近 5 条）';

COMMIT;

-- 说明：历史表从空开始，存量用户的历史密码无法回溯，
-- 从下一次改密/重置起才参与"不能与前 5 次相同"的检查。
