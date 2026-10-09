-- Когда запись последний раз меняли через приложение (цена в админке, статус заказа, профиль).
-- Остаток при заказе списывается отдельным UPDATE и это поле не трогает.
ALTER TABLE product ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE customer_order ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE account ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

UPDATE product SET updated_at = created_at;
UPDATE customer_order SET updated_at = coalesce(paid_at, created_at);
UPDATE account SET updated_at = created_at;
