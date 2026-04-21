-- API Key 可复制能力
-- 兼容 H2 / MySQL / PostgreSQL

ALTER TABLE sl_api_key
    ADD secret_ciphertext TEXT NULL;
