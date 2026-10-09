-- Этап 2: конфигуратор сборки. Добавляем кулеры и характеристики, нужные для проверки совместимости.

-- Процессоры: есть ли встроенная графика и кулер в комплекте.
-- LGA1700 работает и с DDR4, и с DDR5 — тип памяти тогда определяет материнская плата.
UPDATE product SET attributes = attributes || '{"igpu": true, "box_cooler": true}' WHERE slug = 'amd-ryzen-5-7600';
UPDATE product SET attributes = attributes || '{"igpu": true, "box_cooler": false}'
WHERE slug IN ('amd-ryzen-5-9600x', 'amd-ryzen-7-7800x3d', 'amd-ryzen-7-9700x', 'amd-ryzen-9-9900x', 'intel-core-ultra-7-265k');
UPDATE product SET attributes = attributes || '{"igpu": false, "box_cooler": true}' WHERE slug = 'amd-ryzen-5-5600';
UPDATE product SET attributes = attributes || '{"igpu": false, "box_cooler": true, "memory_type": "DDR4, DDR5"}' WHERE slug = 'intel-core-i5-14400f';
UPDATE product SET attributes = attributes || '{"igpu": false, "box_cooler": false, "memory_type": "DDR4, DDR5"}' WHERE slug = 'intel-core-i7-14700kf';

-- Материнские платы: есть ли слот M.2 с PCIe 5.0 (иначе быстрый SSD будет работать вполсилы)
UPDATE product SET attributes = attributes || '{"m2_gen5": true}'
WHERE slug IN ('gigabyte-x870-aorus-elite-wifi7', 'gigabyte-z890-eagle-wifi7');
UPDATE product SET attributes = attributes || '{"m2_gen5": false}'
WHERE category_id = (SELECT id FROM category WHERE slug = 'motherboard') AND NOT attributes ? 'm2_gen5';

-- Корпуса: максимальная высота башенного кулера и длина радиатора СЖО
UPDATE product SET attributes = attributes || '{"max_cooler_mm": 163, "max_radiator_mm": 360}' WHERE slug = 'deepcool-cc560';
UPDATE product SET attributes = attributes || '{"max_cooler_mm": 180, "max_radiator_mm": 360}' WHERE slug IN ('lian-li-lancool-216', 'lian-li-lancool-216-white');
UPDATE product SET attributes = attributes || '{"max_cooler_mm": 170, "max_radiator_mm": 360}' WHERE slug = 'fractal-design-north';
UPDATE product SET attributes = attributes || '{"max_cooler_mm": 160, "max_radiator_mm": 360}' WHERE slug = 'zalman-p30';
UPDATE product SET attributes = attributes || '{"max_cooler_mm": 155, "max_radiator_mm": 280}' WHERE slug = 'cooler-master-nr200p';

INSERT INTO category (slug, name, sort_order, filters) VALUES
    ('cooler', 'Кулеры', 8, '[{"key": "cooler_type", "label": "Тип"}, {"key": "max_tdp", "label": "Рассеивает до, Вт"}]');

-- sockets — строка через запятую; height_mm у башенных, radiator_mm у СЖО
INSERT INTO product (category_id, slug, name, brand, price, stock, description, attributes) VALUES
    ((SELECT id FROM category WHERE slug = 'cooler'), 'id-cooling-se-224-xts', 'ID-COOLING SE-224-XTS', 'ID-COOLING', 1990, 25, 'Недорогая башня на 4 тепловых трубках.', '{"cooler_type": "Башенный", "sockets": "AM4, AM5, LGA1700, LGA1851", "height_mm": 154, "max_tdp": 220}'),
    ((SELECT id FROM category WHERE slug = 'cooler'), 'deepcool-ak400', 'DeepCool AK400', 'DeepCool', 2490, 20, 'Тихий кулер для процессоров среднего уровня.', '{"cooler_type": "Башенный", "sockets": "AM4, AM5, LGA1700, LGA1851", "height_mm": 155, "max_tdp": 220}'),
    ((SELECT id FROM category WHERE slug = 'cooler'), 'thermalright-peerless-assassin-120-se', 'Thermalright Peerless Assassin 120 SE', 'Thermalright', 3490, 15, 'Двухбашенный кулер с двумя вентиляторами, справится с топовыми CPU.', '{"cooler_type": "Башенный", "sockets": "AM4, AM5, LGA1700, LGA1851", "height_mm": 155, "max_tdp": 245}'),
    ((SELECT id FROM category WHERE slug = 'cooler'), 'noctua-nh-d15-g2', 'Noctua NH-D15 G2', 'Noctua', 13990, 3, 'Флагманский воздушный кулер, очень тихий.', '{"cooler_type": "Башенный", "sockets": "AM4, AM5, LGA1700, LGA1851", "height_mm": 168, "max_tdp": 250}'),
    ((SELECT id FROM category WHERE slug = 'cooler'), 'noctua-nh-l9a-am5', 'Noctua NH-L9a-AM5', 'Noctua', 5490, 6, 'Низкопрофильный кулер для компактных сборок на AMD.', '{"cooler_type": "Низкопрофильный", "sockets": "AM4, AM5", "height_mm": 37, "max_tdp": 65}'),
    ((SELECT id FROM category WHERE slug = 'cooler'), 'deepcool-le520', 'DeepCool LE520', 'DeepCool', 6490, 8, 'Жидкостное охлаждение с радиатором 240 мм.', '{"cooler_type": "Жидкостный (СЖО)", "sockets": "AM4, AM5, LGA1700, LGA1851", "radiator_mm": 240, "max_tdp": 250}'),
    ((SELECT id FROM category WHERE slug = 'cooler'), 'arctic-liquid-freezer-iii-360', 'Arctic Liquid Freezer III 360', 'Arctic', 10990, 5, 'Мощная СЖО на 360 мм для самых горячих процессоров.', '{"cooler_type": "Жидкостный (СЖО)", "sockets": "AM4, AM5, LGA1700, LGA1851", "radiator_mm": 360, "max_tdp": 300}');

-- Сохранённые сборки покупателя. items — {"cpu": 1, "mb": 12, ...}: слот конфигуратора → id товара
CREATE TABLE saved_build (
    id          BIGSERIAL PRIMARY KEY,
    account_id  BIGINT       NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    name        VARCHAR(80)  NOT NULL,
    items       JSONB        NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX saved_build_account_idx ON saved_build (account_id, created_at DESC);

-- Сборка в Telegram-боте: одна текущая на чат
CREATE TABLE bot_build (
    chat_id  BIGINT PRIMARY KEY,
    items    JSONB  NOT NULL DEFAULT '{}'
);
