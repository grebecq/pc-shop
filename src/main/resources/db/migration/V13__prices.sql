-- Скидки: цена до скидки. NULL — скидки нет. Всегда больше текущей цены, иначе это не скидка
ALTER TABLE product
    ADD COLUMN old_price INT,
    ADD CONSTRAINT product_old_price_check CHECK (old_price > price);

-- Промокоды: либо процент, либо сумма в рублях. Скидка считается от суммы товаров, доставка не дешевеет
CREATE TABLE promo_code (
    code        VARCHAR(32) PRIMARY KEY,                          -- в верхнем регистре
    percent     INT CHECK (percent BETWEEN 1 AND 90),
    amount      INT CHECK (amount > 0),
    min_total   INT         NOT NULL DEFAULT 0 CHECK (min_total >= 0),  -- от какой суммы товаров действует
    expires_at  TIMESTAMPTZ,                                       -- NULL — бессрочный
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    CHECK ((percent IS NULL) <> (amount IS NULL))
);

ALTER TABLE customer_order
    ADD COLUMN promo_code VARCHAR(32),                                          -- код храним текстом: заказ не зависит от судьбы промокода
    ADD COLUMN discount   INT NOT NULL DEFAULT 0 CHECK (discount >= 0);         -- уже вычтена из total

-- «Сообщить о поступлении»: подписка покупателя на товар, которого нет в наличии. Письмо ушло — подписка удаляется
CREATE TABLE stock_alert (
    account_id  BIGINT      NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    product_id  BIGINT      NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, product_id)
);

CREATE INDEX stock_alert_product_idx ON stock_alert (product_id);

-- Демо-скидки. Условие на цену — чтобы миграция не упала, если цену уже поменяли в админке
UPDATE product p
SET old_price = d.old_price
FROM (VALUES
    ('amd-ryzen-7-7800x3d-oem', 32990),
    ('intel-core-i5-14400f-oem', 16990),
    ('msi-pro-b650-p-wifi', 15290),
    ('gigabyte-b760m-ds3h-ddr4', 11990),
    ('kingston-fury-beast-32-gb-2x16-ddr5-5600-cl40', 69990),
    ('asus-dual-geforce-rtx-5070-oc-12-gb', 104990),
    ('sapphire-pulse-radeon-rx-9070-xt-16-gb', 114990),
    ('wd-black-sn850x-2-tb-m-2-nvme', 41990),
    ('deepcool-pn850m-850-vt', 12490),
    ('deepcool-cc560-v2-black', 7490),
    ('thermalright-peerless-assassin-120-se', 3790)
) AS d (slug, old_price)
WHERE p.slug = d.slug AND p.price < d.old_price;

INSERT INTO promo_code (code, percent, amount, min_total, expires_at) VALUES
    ('SBORKA10', 10, NULL, 30000, NULL),
    ('WELCOME500', NULL, 500, 5000, NULL),
    ('LETO2026', 15, NULL, 0, '2026-09-01 00:00+03');   -- уже закончился: для проверки сообщения об ошибке

-- Одна видеокарта закончилась: на её странице видно кнопку «Сообщить о поступлении»
UPDATE product SET stock = 0 WHERE slug = 'zotac-gaming-geforce-rtx-5080-solid-core-oc-16-gb';
