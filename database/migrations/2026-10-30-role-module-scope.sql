-- 角色模块级数据范围：让"人事看全公司档案、绩效只看本部门"这类需求可配置
-- 已上线的库执行本脚本；空库直接跑 database/schema.sql 即可

BEGIN;

CREATE TABLE IF NOT EXISTS sys_role_module_scope (
    role_id     BIGINT      NOT NULL REFERENCES sys_role (id) ON DELETE CASCADE,
    module_code VARCHAR(32) NOT NULL,
    scope_type  VARCHAR(20) NOT NULL,
    PRIMARY KEY (role_id, module_code)
);

COMMENT ON TABLE sys_role_module_scope IS '角色在单个模块上的数据范围覆盖，未配置的模块沿用 sys_role.data_scope';

COMMIT;
