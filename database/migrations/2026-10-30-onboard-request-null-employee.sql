-- 入职申请允许先不关联员工（3.2-7 入职流程空转）
-- 背景：此前 employee_change_request.employee_id NOT NULL，入职流程必须先由人事在员工档案里
--       手工建档（PENDING），"入职申请"只负责把状态改成试用期，流程本身不产生数据。
-- 变更：employee_id 改为可空。入职申请在 payload 里携带新员工信息，批准时才创建档案并开通账号；
--       其余类型（转正/调岗/离职）仍必须指定员工，服务层已做校验。
-- 影响：存量数据不受影响；无数据回填需求。

BEGIN;

ALTER TABLE employee_change_request
    ALTER COLUMN employee_id DROP NOT NULL;

COMMENT ON COLUMN employee_change_request.employee_id IS
    '关联员工；入职申请批准前可为空（新员工由流程建档）';

COMMIT;
