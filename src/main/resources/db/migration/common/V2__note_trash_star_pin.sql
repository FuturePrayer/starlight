-- 为笔记增加回收站、收藏、置顶能力
-- deleted_at: 软删除时间，非空表示进入回收站
-- pinned_flag / pinned_at: 置顶状态与置顶时间

ALTER TABLE sl_note ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE sl_note ADD COLUMN pinned_flag BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sl_note ADD COLUMN pinned_at TIMESTAMP NULL;

CREATE INDEX idx_sl_note_owner_deleted_at ON sl_note(owner_id, deleted_at);
CREATE INDEX idx_sl_note_owner_pinned_at ON sl_note(owner_id, pinned_flag, pinned_at);

