-- 图片资产上传与引用关系
-- 兼容 H2 / MySQL / PostgreSQL

CREATE TABLE sl_asset (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    owner_id VARCHAR(36) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    storage_provider VARCHAR(20) NOT NULL,
    object_key VARCHAR(800) NOT NULL,
    read_token VARCHAR(64) NOT NULL,
    unreferenced_since TIMESTAMP NULL,
    deleted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_sl_asset_read_token UNIQUE (read_token),
    CONSTRAINT fk_sl_asset_owner FOREIGN KEY (owner_id) REFERENCES sl_user(id)
);

CREATE TABLE sl_note_asset_ref (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    note_id VARCHAR(36) NOT NULL,
    asset_id VARCHAR(36) NOT NULL,
    owner_id VARCHAR(36) NOT NULL,
    referenced_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_sl_note_asset_ref_note_asset UNIQUE (note_id, asset_id),
    CONSTRAINT fk_sl_note_asset_ref_note FOREIGN KEY (note_id) REFERENCES sl_note(id),
    CONSTRAINT fk_sl_note_asset_ref_asset FOREIGN KEY (asset_id) REFERENCES sl_asset(id),
    CONSTRAINT fk_sl_note_asset_ref_owner FOREIGN KEY (owner_id) REFERENCES sl_user(id)
);

CREATE INDEX idx_sl_asset_owner ON sl_asset(owner_id);
CREATE INDEX idx_sl_asset_deleted_ref ON sl_asset(deleted_at, unreferenced_since);
CREATE INDEX idx_sl_note_asset_ref_note ON sl_note_asset_ref(note_id);
CREATE INDEX idx_sl_note_asset_ref_asset ON sl_note_asset_ref(asset_id);
CREATE INDEX idx_sl_note_asset_ref_owner ON sl_note_asset_ref(owner_id);

INSERT INTO sl_app_setting (setting_key, setting_value, created_at, updated_at)
VALUES ('assets.upload.enabled', 'false', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO sl_app_setting (setting_key, setting_value, created_at, updated_at)
VALUES ('assets.storage.provider', 'local', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO sl_app_setting (setting_key, setting_value, created_at, updated_at)
VALUES ('assets.user.quota-bytes', '104857600', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO sl_app_setting (setting_key, setting_value, created_at, updated_at)
VALUES ('assets.cleanup.grace-hours', '168', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
