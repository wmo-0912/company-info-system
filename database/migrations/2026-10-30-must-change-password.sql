-- 首次登录强制改密（第 2 轮：改密 / 管理员重置密码 / 首登强制改密）
--
-- 背景：登录成功后需要告诉前端"是否必须改密"，UserServiceImpl 会读取 sys_user.must_change_password。
--       该列当时只加进了 database/schema.sql，漏了存量库的迁移脚本，导致旧库升级后登录直接报
--       org.postgresql.util.PSQLException: 错误: 字段 "must_change_password" 不存在。
--
-- 执行：psql -U postgres -d company_info -f database/migrations/2026-10-30-must-change-password.sql
-- 影响：只加列，默认 FALSE；存量账号不会被强制改密（需要时由管理员重置密码触发）。

ALTER TABLE sys_user
    ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN sys_user.must_change_password IS '首次登录/管理员重置密码后为 TRUE，改密成功后置回 FALSE';
