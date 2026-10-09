-- Отзывы покупателей. Оценку и число отзывов храним прямо в товаре: они нужны в каждой карточке каталога
-- и для сортировки, а пересчитываются только при новом отзыве
ALTER TABLE product
    ADD COLUMN rating        NUMERIC(2, 1),          -- средняя оценка, NULL — отзывов нет
    ADD COLUMN reviews_count INT NOT NULL DEFAULT 0;

CREATE TABLE review (
    id           BIGSERIAL PRIMARY KEY,
    product_id   BIGINT        NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    account_id   BIGINT        REFERENCES account (id) ON DELETE SET NULL,  -- NULL у демо-отзывов и удалённых аккаунтов
    author_name  VARCHAR(128)  NOT NULL,
    rating       INT           NOT NULL CHECK (rating BETWEEN 1 AND 5),
    pros         VARCHAR(1000),
    cons         VARCHAR(1000),
    text         VARCHAR(2000) NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (product_id, account_id)                                         -- один отзыв на товар от покупателя
);

CREATE INDEX review_product_idx ON review (product_id, created_at DESC);

-- Избранное покупателя
CREATE TABLE favorite (
    account_id  BIGINT      NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    product_id  BIGINT      NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, product_id)
);

-- Несколько отзывов для демо, чтобы карточки и страницы товаров не были пустыми
INSERT INTO review (product_id, author_name, rating, pros, cons, text, created_at)
SELECT p.id, r.author, r.rating, r.pros, r.cons, r.text, now() - make_interval(days => r.days_ago)
FROM (VALUES
    ('amd-ryzen-5-7600-oem', 'Алексей', 5, 'Холодный, быстрый, AM5 на будущее', 'Кулера в комплекте нет', 'Собрал игровой ПК под 1440p, в играх упора в процессор не вижу. С башенным кулером до 70 градусов.', 40),
    ('amd-ryzen-5-7600-oem', 'Марина', 4, 'Цена и производительность', 'Нужна DDR5, апгрейд вышел дороже', 'Переходила с AM4. Разница заметная, но память и плату пришлось менять.', 25),
    ('amd-ryzen-5-7600-oem', 'Денис', 5, NULL, NULL, 'Отличный процессор за свои деньги, работает без нареканий.', 6),
    ('intel-core-i5-12400f-oem', 'Игорь', 5, 'Недорогой, хватает для игр', 'Без встроенной графики', 'Брал в офисно-игровую сборку. Шустрый, тихий с простым кулером.', 60),
    ('intel-core-i5-12400f-oem', 'Ольга', 4, 'Цена', 'Платформа уже не новая', 'Для своих денег хорош, но на будущее лучше смотреть AM5.', 18),
    ('asus-dual-geforce-rtx-5070-oc-12-gb', 'Сергей', 5, 'Тихая под нагрузкой, 12 ГБ памяти', 'Длинная, проверьте корпус', 'В 1440p на высоких всё летает, DLSS выручает в тяжёлых играх.', 30),
    ('asus-dual-geforce-rtx-5070-oc-12-gb', 'Павел', 4, 'Производительность', 'Хотелось бы дешевле', 'Карта отличная, но цена кусается. Шума почти нет.', 12),
    ('asrock-intel-arc-b580-challenger-oc-12-gb', 'Никита', 4, '12 ГБ за небольшие деньги', 'В старых играх бывает хуже', 'Для бюджетной сборки отличный вариант, драйверы стали заметно лучше.', 20),
    ('samsung-990-pro-1-tb-m-2-nvme', 'Андрей', 5, 'Очень быстрый', NULL, 'Система грузится за секунды, под нагрузкой не греется с радиатором платы.', 45),
    ('kingston-fury-beast-16-gb-2x8-ddr5-5600-cl40', 'Елена', 5, 'Завелась сразу с EXPO', NULL, 'Поставила в сборку на B650, всё стабильно.', 15),
    ('deepcool-pn750d-750-vt', 'Роман', 4, 'Тихий, хватает с запасом', 'Не модульный', 'Для сборки с RTX 5060 мощности хватает, кабели жёсткие, но уложил.', 33),
    ('thermalright-peerless-assassin-120-se', 'Виктор', 5, 'Охлаждает как топовые модели', 'Большой, закрывает первый слот памяти', 'Лучший кулер за свои деньги, Ryzen 7 держит без проблем.', 22),
    ('thermalright-peerless-assassin-120-se', 'Кирилл', 5, 'Тихий', NULL, 'Ставил на Intel, крепление простое, температуры отличные.', 9),
    ('lian-li-lancool-216x-black', 'Артём', 5, 'Продуваемый, много места', NULL, 'Собирать удобно, видеокарта 330 мм встала без проблем.', 28)
) AS r (slug, author, rating, pros, cons, text, days_ago)
JOIN product p ON p.slug = r.slug;

UPDATE product p
SET rating = s.rating, reviews_count = s.cnt
FROM (SELECT product_id, round(avg(rating), 1) AS rating, count(*) AS cnt FROM review GROUP BY product_id) s
WHERE p.id = s.product_id;
