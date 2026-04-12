-- Git 仓库导入与同步支持
-- 设计目标：兼容 H2 / MySQL 8+ / PostgreSQL

CREATE TABLE sl_git_note_source (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    owner_id VARCHAR(36) NOT NULL,
    repository_url TEXT NOT NULL,
    repository_name VARCHAR(200) NOT NULL,
    branch_name VARCHAR(200) NOT NULL,
    source_path VARCHAR(500) NOT NULL,
    target_category_id VARCHAR(36) NULL,
    target_category_name VARCHAR(200) NOT NULL,
    target_category_created_by_source BOOLEAN NOT NULL DEFAULT FALSE,
    auto_sync_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    schedule_type VARCHAR(40) NOT NULL,
    schedule_timezone VARCHAR(80) NULL,
    schedule_hour INT NULL,
    schedule_minute INT NULL,
    schedule_day_of_week INT NULL,
    last_synced_commit_id VARCHAR(80) NULL,
    last_sync_at TIMESTAMP NULL,
    last_sync_success BOOLEAN NULL,
    last_sync_message TEXT NULL,
    last_scheduled_run_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_sl_git_note_source_owner FOREIGN KEY (owner_id) REFERENCES sl_user(id),
    CONSTRAINT fk_sl_git_note_source_target_category FOREIGN KEY (target_category_id) REFERENCES sl_category(id)
);

CREATE TABLE sl_git_sync_history (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    source_id VARCHAR(36) NOT NULL,
    trigger_type VARCHAR(20) NOT NULL,
    success_flag BOOLEAN NOT NULL,
    commit_id VARCHAR(80) NULL,
    message TEXT NULL,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_sl_git_sync_history_source FOREIGN KEY (source_id) REFERENCES sl_git_note_source(id)
);

CREATE TABLE sl_git_import_binding (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    source_id VARCHAR(36) NOT NULL,
    binding_type VARCHAR(20) NOT NULL,
    entity_id VARCHAR(36) NOT NULL,
    relative_path VARCHAR(500) NOT NULL,
    content_hash VARCHAR(80) NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_sl_git_import_binding_source FOREIGN KEY (source_id) REFERENCES sl_git_note_source(id)
);

CREATE INDEX idx_sl_git_note_source_owner ON sl_git_note_source(owner_id);
CREATE INDEX idx_sl_git_note_source_auto_sync ON sl_git_note_source(auto_sync_enabled);
CREATE INDEX idx_sl_git_note_source_target_category ON sl_git_note_source(target_category_id);
CREATE INDEX idx_sl_git_sync_history_source_started ON sl_git_sync_history(source_id, started_at);
CREATE INDEX idx_sl_git_import_binding_source ON sl_git_import_binding(source_id);
CREATE INDEX idx_sl_git_import_binding_entity ON sl_git_import_binding(entity_id);

INSERT INTO sl_app_setting (setting_key, setting_value, created_at, updated_at)
VALUES ('git.import.enabled', 'false', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO sl_app_setting (setting_key, setting_value, created_at, updated_at)
VALUES ('git.import.max-concurrent', '2', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

