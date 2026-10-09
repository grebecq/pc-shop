-- Аккаунты покупателей сайта. email храним в нижнем регистре, пароль — только хеш (bcrypt)
CREATE TABLE account (
    id             BIGSERIAL PRIMARY KEY,
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    name           VARCHAR(128) NOT NULL,
    phone          VARCHAR(32),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Сохранённые адреса доставки. Один из них может быть адресом по умолчанию
CREATE TABLE delivery_address (
    id           BIGSERIAL PRIMARY KEY,
    account_id   BIGINT       NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    label        VARCHAR(64)  NOT NULL,      -- «Дом», «Работа»
    city         VARCHAR(128) NOT NULL,
    street       VARCHAR(256) NOT NULL,      -- улица и дом
    apartment    VARCHAR(32),
    postal_code  VARCHAR(16),
    is_default   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX delivery_address_account_idx ON delivery_address (account_id);
-- Не больше одного адреса по умолчанию у аккаунта
CREATE UNIQUE INDEX delivery_address_default_idx ON delivery_address (account_id) WHERE is_default;

ALTER TABLE customer_order ADD COLUMN account_id BIGINT REFERENCES account (id);
CREATE INDEX customer_order_account_idx ON customer_order (account_id, created_at DESC) WHERE account_id IS NOT NULL;

-- Фото товара: ссылка на картинку (своя статика или загруженный файл)
ALTER TABLE product ADD COLUMN image_url VARCHAR(512);

-- Фото, загруженные через админку. Хранятся в базе, чтобы не настраивать отдельное хранилище файлов
CREATE TABLE product_photo (
    product_id    BIGINT       PRIMARY KEY REFERENCES product (id) ON DELETE CASCADE,
    content_type  VARCHAR(32)  NOT NULL,
    data          BYTEA        NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
