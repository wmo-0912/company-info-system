-- 3.2-6 调岗不再写入 employment_status = 'TRANSFERRED'
--
-- 背景：调岗是一次性事件而不是在职状态。历史上 TRANSFERRED 被写进 employee.employment_status，
-- 导致调岗后无法转正（转正只允许 PROBATION）、在职筛选要硬拼 "PROBATION,REGULAR,TRANSFERRED"、
-- 前端状态列把"已调岗"当状态展示。
--
-- 本脚本把历史数据回落为正式员工（从未转正就调岗的试用期员工若需要，请按 employee_employment_record
-- 人工核对后改回 PROBATION）。调岗事实本身仍保留在 employee_employment_record 与审计日志中，不会丢。
--
-- 执行：psql -U postgres -d company_info -f database/migrations/2026-10-30-remove-transferred-status.sql

BEGIN;

-- 1) 先看影响面（可与下面 UPDATE 分开执行）
SELECT id, employee_no, employee_name, employment_status
FROM employee
WHERE employment_status = 'TRANSFERRED'
ORDER BY id;

-- 2) 回落为正式员工
UPDATE employee
SET employment_status = 'REGULAR'
WHERE employment_status = 'TRANSFERRED';

COMMIT;
