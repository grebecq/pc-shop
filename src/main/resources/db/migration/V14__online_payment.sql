-- Онлайн-оплата через ЮKassa
ALTER TABLE customer_order
    ADD COLUMN payment_id  VARCHAR(64),   -- последний платёж в ЮKassa: им заказ оплачен или по нему покупатель сейчас платит
    ADD COLUMN refunded_at TIMESTAMPTZ;   -- когда вернули деньги за отменённый заказ
