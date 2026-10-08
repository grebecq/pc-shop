-- Категории товаров. filters — какие характеристики показывать в фильтре каталога:
-- [{"key": "socket", "label": "Сокет"}, ...]
CREATE TABLE category (
    id          BIGSERIAL PRIMARY KEY,
    slug        VARCHAR(64)  NOT NULL UNIQUE,
    name        VARCHAR(128) NOT NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    filters     JSONB        NOT NULL DEFAULT '[]'
);

-- Товар. Характеристики у разных категорий разные, поэтому лежат в JSONB:
-- процессор: {"socket": "AM5", "cores": 8, "tdp": 65}, блок питания: {"power": 750} и т.д.
CREATE TABLE product (
    id           BIGSERIAL PRIMARY KEY,
    category_id  BIGINT       NOT NULL REFERENCES category (id),
    slug         VARCHAR(128) NOT NULL UNIQUE,
    name         VARCHAR(256) NOT NULL,
    brand        VARCHAR(64)  NOT NULL,
    price        INT          NOT NULL CHECK (price > 0),  -- в рублях
    stock        INT          NOT NULL DEFAULT 0 CHECK (stock >= 0),
    description  TEXT         NOT NULL DEFAULT '',
    attributes   JSONB        NOT NULL DEFAULT '{}',
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX product_category_idx ON product (category_id) WHERE active;
-- GIN-индекс для фильтрации по характеристикам (оператор @>)
CREATE INDEX product_attributes_idx ON product USING GIN (attributes jsonb_path_ops);

CREATE TABLE customer_order (
    id                BIGSERIAL PRIMARY KEY,
    public_id         UUID         NOT NULL UNIQUE,      -- для ссылок клиенту, чтобы нельзя было перебрать заказы
    channel           VARCHAR(16)  NOT NULL,             -- SITE | TELEGRAM
    status            VARCHAR(16)  NOT NULL,
    customer_name     VARCHAR(128) NOT NULL,
    phone             VARCHAR(32)  NOT NULL,
    address           VARCHAR(512) NOT NULL,
    comment           VARCHAR(1000),
    telegram_chat_id  BIGINT,
    total             INT          NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    paid_at           TIMESTAMPTZ
);

CREATE INDEX customer_order_created_idx ON customer_order (created_at DESC);
CREATE INDEX customer_order_chat_idx ON customer_order (telegram_chat_id) WHERE telegram_chat_id IS NOT NULL;

CREATE TABLE order_item (
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT       NOT NULL REFERENCES customer_order (id) ON DELETE CASCADE,
    product_id  BIGINT       NOT NULL REFERENCES product (id),
    name        VARCHAR(256) NOT NULL,  -- копия названия и цены на момент заказа
    price       INT          NOT NULL,
    quantity    INT          NOT NULL CHECK (quantity > 0)
);

-- Корзина в Telegram-боте (на сайте корзина хранится в браузере)
CREATE TABLE bot_cart_item (
    chat_id     BIGINT NOT NULL,
    product_id  BIGINT NOT NULL REFERENCES product (id),
    quantity    INT    NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (chat_id, product_id)
);
