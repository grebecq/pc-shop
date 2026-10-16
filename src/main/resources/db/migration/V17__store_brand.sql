ALTER TABLE store_info
    ADD COLUMN name         VARCHAR(64)  NOT NULL DEFAULT 'Сборка',
    ADD COLUMN tagline      VARCHAR(128) NOT NULL DEFAULT 'комплектующие и сборка ПК',
    ADD COLUMN accent       VARCHAR(7)   NOT NULL DEFAULT '#2b50d9' CHECK (accent ~ '^#[0-9a-f]{6}$'),
    ADD COLUMN logo_version BIGINT;

CREATE TABLE store_logo (
    id           SMALLINT    PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    content_type VARCHAR(32) NOT NULL,
    data         BYTEA       NOT NULL
);
