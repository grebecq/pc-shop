-- Ссылки для смены пароля из письма. Сам токен есть только в письме, в базе — его SHA-256 (hex):
-- по содержимому таблицы ссылку не восстановить
CREATE TABLE password_reset_token (
    id          BIGSERIAL PRIMARY KEY,
    account_id  BIGINT      NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Ссылки аккаунта: проверка «не чаще одного письма в минуту» и удаление старых ссылок
CREATE INDEX password_reset_token_account_idx ON password_reset_token (account_id, created_at DESC);
