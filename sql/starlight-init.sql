-- starlight 初始化表结构
-- 设计目标：尽量兼容 PostgreSQL / H2 / MySQL 8+

DROP TABLE IF EXISTS sl_user_credential;
DROP TABLE IF EXISTS sl_note_share;
DROP TABLE IF EXISTS sl_note;
DROP TABLE IF EXISTS sl_category;
DROP TABLE IF EXISTS sl_user;
DROP TABLE IF EXISTS sl_app_setting;

CREATE TABLE sl_app_setting (
    setting_key VARCHAR(120) NOT NULL PRIMARY KEY,
    setting_value TEXT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE sl_user (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    username VARCHAR(120) NOT NULL,
    email VARCHAR(160) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    admin_flag BOOLEAN NOT NULL,
    theme_id VARCHAR(120) NOT NULL,
    totp_secret VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_sl_user_username UNIQUE (username),
    CONSTRAINT uk_sl_user_email UNIQUE (email)
);

CREATE TABLE sl_category (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    owner_id VARCHAR(36) NOT NULL,
    parent_id VARCHAR(36) NULL,
    name VARCHAR(200) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_sl_category_owner FOREIGN KEY (owner_id) REFERENCES sl_user(id),
    CONSTRAINT fk_sl_category_parent FOREIGN KEY (parent_id) REFERENCES sl_category(id)
);

CREATE TABLE sl_note (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    owner_id VARCHAR(36) NOT NULL,
    category_id VARCHAR(36) NULL,
    title VARCHAR(255) NOT NULL,
    markdown_content TEXT NOT NULL,
    rendered_html TEXT NOT NULL,
    outline_json TEXT NOT NULL,
    plain_text TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_sl_note_owner FOREIGN KEY (owner_id) REFERENCES sl_user(id),
    CONSTRAINT fk_sl_note_category FOREIGN KEY (category_id) REFERENCES sl_category(id)
);

CREATE TABLE sl_note_share (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    note_id VARCHAR(36) NOT NULL,
    owner_id VARCHAR(36) NOT NULL,
    token VARCHAR(64) NOT NULL,
    access_type VARCHAR(20) NOT NULL,
    password_hash VARCHAR(255) NULL,
    expires_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_sl_note_share_token UNIQUE (token),
    CONSTRAINT fk_sl_note_share_note FOREIGN KEY (note_id) REFERENCES sl_note(id),
    CONSTRAINT fk_sl_note_share_owner FOREIGN KEY (owner_id) REFERENCES sl_user(id)
);

CREATE TABLE sl_user_credential (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    credential_id TEXT NOT NULL,
    public_key_cose TEXT NOT NULL,
    signature_count BIGINT NOT NULL DEFAULT 0,
    nickname VARCHAR(100) NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_sl_user_credential_user FOREIGN KEY (user_id) REFERENCES sl_user(id)
);

CREATE INDEX idx_sl_category_owner ON sl_category(owner_id);
CREATE INDEX idx_sl_category_parent ON sl_category(parent_id);
CREATE INDEX idx_sl_note_owner ON sl_note(owner_id);
CREATE INDEX idx_sl_note_category ON sl_note(category_id);
CREATE INDEX idx_sl_note_share_note_owner ON sl_note_share(note_id, owner_id);
CREATE INDEX idx_sl_user_credential_user ON sl_user_credential(user_id);

INSERT INTO sl_app_setting (setting_key, setting_value, created_at, updated_at)
VALUES ('registration.enabled', 'false', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO sl_app_setting (setting_key, setting_value, created_at, updated_at)
VALUES ('share.base-url', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO sl_app_setting (setting_key, setting_value, created_at, updated_at)
VALUES ('totp.enabled', 'false', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO sl_app_setting (setting_key, setting_value, created_at, updated_at)
VALUES ('passkey.enabled', 'false', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
