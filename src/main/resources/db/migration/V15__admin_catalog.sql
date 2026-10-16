-- Характеристики категории теперь задаёт владелец в админке. specs — какие поля есть у товаров категории, по порядку вывода:
-- key — ключ в product.attributes, type — NUMBER (целое число) | TEXT | BOOL, filter — показывать в фильтре каталога.
-- Какие из них обязательны для конфигуратора сборок, задано в коде (Slot.specs).
-- Бывший столбец filters — это specs с filter = true.
ALTER TABLE category ADD COLUMN specs JSONB NOT NULL DEFAULT '[]';

UPDATE category SET specs = '[
  {"key": "socket", "label": "Сокет", "type": "TEXT", "filter": true},
  {"key": "cores", "label": "Ядер", "type": "NUMBER", "filter": true},
  {"key": "boost_ghz", "label": "Частота в бусте, ГГц", "type": "TEXT"},
  {"key": "threads", "label": "Потоков", "type": "NUMBER"},
  {"key": "memory_type", "label": "Тип памяти", "type": "TEXT"},
  {"key": "tdp", "label": "Тепловыделение, Вт", "type": "NUMBER"},
  {"key": "igpu", "label": "Встроенная графика", "type": "BOOL"},
  {"key": "box_cooler", "label": "Кулер в комплекте", "type": "BOOL"}
]' WHERE slug = 'cpu';

UPDATE category SET specs = '[
  {"key": "socket", "label": "Сокет", "type": "TEXT", "filter": true},
  {"key": "chipset", "label": "Чипсет", "type": "TEXT", "filter": true},
  {"key": "memory_type", "label": "Тип памяти", "type": "TEXT", "filter": true},
  {"key": "form_factor", "label": "Форм-фактор", "type": "TEXT", "filter": true},
  {"key": "m2_gen5", "label": "M.2 с PCIe 5.0", "type": "BOOL"},
  {"key": "wifi", "label": "Wi-Fi", "type": "BOOL"}
]' WHERE slug = 'motherboard';

UPDATE category SET specs = '[
  {"key": "memory_type", "label": "Тип памяти", "type": "TEXT", "filter": true},
  {"key": "capacity_gb", "label": "Объём, ГБ", "type": "NUMBER", "filter": true},
  {"key": "modules", "label": "Модули, ГБ", "type": "TEXT"},
  {"key": "frequency", "label": "Частота, МГц", "type": "NUMBER", "filter": true},
  {"key": "cl", "label": "Задержка CL", "type": "NUMBER"}
]' WHERE slug = 'ram';

UPDATE category SET specs = '[
  {"key": "gpu_chip", "label": "Видеочип", "type": "TEXT", "filter": true},
  {"key": "memory_gb", "label": "Видеопамять, ГБ", "type": "NUMBER", "filter": true},
  {"key": "vram_type", "label": "Тип видеопамяти", "type": "TEXT"},
  {"key": "chip_vendor", "label": "Производитель чипа", "type": "TEXT", "filter": true},
  {"key": "boost_mhz", "label": "Частота в бусте, МГц", "type": "NUMBER"},
  {"key": "length_mm", "label": "Длина, мм", "type": "NUMBER"},
  {"key": "slots", "label": "Толщина, слотов", "type": "NUMBER"},
  {"key": "recommended_psu", "label": "Рекомендуемый БП, Вт", "type": "NUMBER"},
  {"key": "tdp", "label": "Тепловыделение, Вт", "type": "NUMBER"}
]' WHERE slug = 'gpu';

UPDATE category SET specs = '[
  {"key": "capacity_gb", "label": "Объём, ГБ", "type": "NUMBER", "filter": true},
  {"key": "interface", "label": "Интерфейс", "type": "TEXT", "filter": true},
  {"key": "read_mbps", "label": "Чтение, МБ/с", "type": "NUMBER"}
]' WHERE slug = 'ssd';

UPDATE category SET specs = '[
  {"key": "power", "label": "Мощность, Вт", "type": "NUMBER", "filter": true},
  {"key": "certificate", "label": "Сертификат", "type": "TEXT", "filter": true},
  {"key": "modular", "label": "Модульный", "type": "BOOL"},
  {"key": "atx3", "label": "ATX 3.x с разъёмом 12V-2x6", "type": "BOOL"}
]' WHERE slug = 'psu';

UPDATE category SET specs = '[
  {"key": "form_factor", "label": "Макс. плата", "type": "TEXT", "filter": true},
  {"key": "max_gpu_mm", "label": "Макс. длина видеокарты, мм", "type": "NUMBER"},
  {"key": "max_cooler_mm", "label": "Макс. высота кулера, мм", "type": "NUMBER"},
  {"key": "max_radiator_mm", "label": "Макс. радиатор СЖО, мм", "type": "NUMBER"},
  {"key": "color", "label": "Цвет", "type": "TEXT", "filter": true}
]' WHERE slug = 'case';

UPDATE category SET specs = '[
  {"key": "cooler_type", "label": "Тип", "type": "TEXT", "filter": true},
  {"key": "max_tdp", "label": "Рассеивает до, Вт", "type": "NUMBER", "filter": true},
  {"key": "height_mm", "label": "Высота, мм", "type": "NUMBER"},
  {"key": "radiator_mm", "label": "Радиатор, мм", "type": "NUMBER"},
  {"key": "sockets", "label": "Сокеты", "type": "TEXT"}
]' WHERE slug = 'cooler';

ALTER TABLE category DROP COLUMN filters;

-- Журнал склада: каждое изменение остатка с причиной. Остаток товара меняется только вместе с записью сюда
CREATE TABLE stock_movement (
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT       NOT NULL REFERENCES product (id),
    reason      VARCHAR(16)  NOT NULL,                        -- RECEIPT | WRITE_OFF | COUNT | SALE | RETURN
    delta       INT          NOT NULL,                        -- сколько добавилось (+) или ушло (−)
    stock_after INT          NOT NULL CHECK (stock_after >= 0),
    order_id    BIGINT       REFERENCES customer_order (id),  -- для продаж и возвратов
    comment     VARCHAR(256),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX stock_movement_product_idx ON stock_movement (product_id, id DESC);
