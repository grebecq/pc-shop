-- Чем оплачен заказ на сайте: платёжная система и последние 4 цифры карты, например «Visa •• 4242».
-- Номер карты целиком и CVC не храним нигде.
ALTER TABLE customer_order ADD COLUMN payment_card VARCHAR(32);
