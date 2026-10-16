-- Контакты и реквизиты продавца: владелец меняет их в админке (Настройки → Магазин).
-- Строка всегда одна. Пока продавец (legal_name и inn) не указан, сайт показывает пометку «демо-магазин»
CREATE TABLE store_info (
    id            SMALLINT     PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    phone         VARCHAR(32)  NOT NULL,
    email         VARCHAR(128) NOT NULL,
    hours         VARCHAR(128) NOT NULL,
    legal_name    VARCHAR(256),
    inn           VARCHAR(12),
    ogrn          VARCHAR(15),
    legal_address VARCHAR(512),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO store_info (phone, email, hours)
VALUES ('8 800 000-00-00', 'hello@pc-shop.local', 'ежедневно 9:00–21:00, звонок бесплатный');

-- Подтверждение почты. Ссылка одна на аккаунт: новая заменяет прежнюю. В базе только SHA-256 токена, как у смены пароля
ALTER TABLE account
    ADD COLUMN email_confirmed_at  TIMESTAMPTZ,
    ADD COLUMN email_token_hash    VARCHAR(64) UNIQUE,
    ADD COLUMN email_token_sent_at TIMESTAMPTZ;
