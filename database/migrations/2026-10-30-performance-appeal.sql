-- 绩效申诉：3.3「无员工申诉」补齐
-- 背景：员工对已提交的绩效评价有异议时，只能线下找人事；评价人改稿也没有正式触发渠道。
-- 变更：新增 performance_appeal，一条申诉对应一条评价；受理（ACCEPTED）时把评价置为 RETURNED，
--       仍走「退回 → 改稿 → 重新提交」，申诉不会直接改分。驳回（REJECTED）必须填写处理意见。
-- 影响：新表，不改动已有列；存量数据无需回填。

BEGIN;

CREATE TABLE IF NOT EXISTS performance_appeal (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    review_id           BIGINT       NOT NULL REFERENCES performance_review (id) ON DELETE CASCADE,
    employee_id         BIGINT       NOT NULL REFERENCES employee (id),
    reason              VARCHAR(500) NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    handler_employee_id BIGINT       NULL REFERENCES employee (id),
    handle_comment      VARCHAR(500) NULL,
    handled_at          TIMESTAMPTZ  NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_appeal_review ON performance_appeal (review_id);
CREATE INDEX IF NOT EXISTS idx_appeal_status ON performance_appeal (status, created_at);

COMMENT ON TABLE performance_appeal IS '绩效申诉：员工对已提交评价提出异议，人事受理或驳回';
COMMENT ON COLUMN performance_appeal.status IS 'PENDING 待处理 / ACCEPTED 已受理（评价退回改稿）/ REJECTED 已驳回';

COMMIT;
