-- MCP 服务与通用 API Key 能力
-- 兼容 H2 / MySQL / PostgreSQL

INSERT INTO sl_app_setting (setting_key, setting_value, created_at, updated_at)
VALUES ('mcp.enabled', 'false', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

CREATE TABLE sl_api_key (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    owner_id VARCHAR(36) NOT NULL,
    name VARCHAR(120) NOT NULL,
    key_prefix VARCHAR(32) NOT NULL,
    secret_hash VARCHAR(64) NOT NULL,
    enabled_flag BOOLEAN NOT NULL DEFAULT TRUE,
    read_only_flag BOOLEAN NOT NULL DEFAULT TRUE,
    allow_all_categories_flag BOOLEAN NOT NULL DEFAULT TRUE,
    last_used_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_sl_api_key_owner FOREIGN KEY (owner_id) REFERENCES sl_user(id),
    CONSTRAINT uk_sl_api_key_secret_hash UNIQUE (secret_hash)
);

CREATE TABLE sl_api_key_scope (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    api_key_id VARCHAR(36) NOT NULL,
    category_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_sl_api_key_scope_key FOREIGN KEY (api_key_id) REFERENCES sl_api_key(id),
    CONSTRAINT fk_sl_api_key_scope_category FOREIGN KEY (category_id) REFERENCES sl_category(id)
);

CREATE INDEX idx_sl_api_key_owner ON sl_api_key(owner_id);
CREATE INDEX idx_sl_api_key_last_used_at ON sl_api_key(last_used_at);
CREATE INDEX idx_sl_api_key_scope_key ON sl_api_key_scope(api_key_id);
CREATE INDEX idx_sl_api_key_scope_category ON sl_api_key_scope(category_id);
CREATE UNIQUE INDEX uk_sl_api_key_scope_key_category ON sl_api_key_scope(api_key_id, category_id);

