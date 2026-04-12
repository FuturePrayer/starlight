-- 分类软删除与回收站目录树支持
-- 设计目标：兼容 H2 / MySQL 8+ / PostgreSQL

ALTER TABLE sl_category
    ADD COLUMN deleted_at TIMESTAMP NULL;

CREATE INDEX idx_sl_category_owner_deleted ON sl_category(owner_id, deleted_at);
CREATE INDEX idx_sl_category_deleted ON sl_category(deleted_at);

