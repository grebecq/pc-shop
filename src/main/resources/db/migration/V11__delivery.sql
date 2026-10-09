-- Способ получения заказа и цена доставки. Старые заказы считаем доставкой курьером, бесплатной
ALTER TABLE customer_order
    ADD COLUMN delivery_method VARCHAR(16) NOT NULL DEFAULT 'COURIER',  -- COURIER | PICKUP
    ADD COLUMN delivery_price  INT         NOT NULL DEFAULT 0 CHECK (delivery_price >= 0),  -- уже входит в total
    ADD COLUMN email           VARCHAR(254);                            -- куда слать письма о заказе, у заказов из бота пусто

-- Магазины для самовывоза
CREATE TABLE pickup_point (
    id       BIGSERIAL PRIMARY KEY,
    name     VARCHAR(128) NOT NULL,
    address  VARCHAR(256) NOT NULL,
    hours    VARCHAR(128) NOT NULL,
    active   BOOLEAN      NOT NULL DEFAULT TRUE
);

INSERT INTO pickup_point (name, address, hours) VALUES
    ('«Сборка» на Тверской', 'Москва, Тверская ул., 12', 'ежедневно 10:00–22:00'),
    ('«Сборка» у Савёловской', 'Москва, ул. Сущёвский Вал, 5', 'ежедневно 10:00–21:00'),
    ('«Сборка» в Бутово', 'Москва, ул. Поляны, 8', 'пн–пт 10:00–21:00, сб–вс 11:00–20:00');
