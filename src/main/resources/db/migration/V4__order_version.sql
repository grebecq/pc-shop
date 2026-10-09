-- Версия строки для оптимистической блокировки: оплата и автоотмена не перезапишут друг друга
ALTER TABLE customer_order ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
CREATE INDEX customer_order_awaiting_idx ON customer_order (created_at) WHERE status = 'AWAITING_PAYMENT';
