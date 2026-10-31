-- 会话版本：已签发的 JWT 在密码修改/重置后立即失效
-- 执行：psql -h localhost -U company_app -d company_info -f 2026-10-30-token-version.sql
BEGIN;

ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS token_version INT NOT NULL DEFAULT 0;

COMMENT ON COLUMN sys_user.token_version IS '会话版本：改密/重置密码时 +1，使旧 token 在下一次请求即失效';

COMMIT;
