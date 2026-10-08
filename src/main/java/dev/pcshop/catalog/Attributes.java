package dev.pcshop.catalog;

import java.util.LinkedHashMap;
import java.util.Map;

/** Человеческие названия характеристик и их форматирование для сайта и бота. */
public final class Attributes {

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("socket", "Сокет"),
            Map.entry("cores", "Ядер"),
            Map.entry("threads", "Потоков"),
            Map.entry("tdp", "Тепловыделение, Вт"),
            Map.entry("memory_type", "Тип памяти"),
            Map.entry("chipset", "Чипсет"),
            Map.entry("form_factor", "Форм-фактор"),
            Map.entry("capacity_gb", "Объём, ГБ"),
            Map.entry("frequency", "Частота, МГц"),
            Map.entry("modules", "Модули, ГБ"),
            Map.entry("chip_vendor", "Производитель чипа"),
            Map.entry("memory_gb", "Видеопамять, ГБ"),
            Map.entry("length_mm", "Длина, мм"),
            Map.entry("recommended_psu", "Рекомендуемый БП, Вт"),
            Map.entry("interface", "Интерфейс"),
            Map.entry("read_mbps", "Чтение, МБ/с"),
            Map.entry("power", "Мощность, Вт"),
            Map.entry("certificate", "Сертификат"),
            Map.entry("modular", "Модульный"),
            Map.entry("max_gpu_mm", "Макс. длина видеокарты, мм"),
            Map.entry("color", "Цвет"));

    private Attributes() {
    }

    public static String label(String key) {
        return LABELS.getOrDefault(key, key);
    }

    public static String value(Object value) {
        if (value instanceof Boolean b) {
            return b ? "да" : "нет";
        }
        return String.valueOf(value);
    }

    /** Характеристики товара в виде «Название → значение» в исходном порядке. */
    public static Map<String, String> readable(Product product) {
        Map<String, String> result = new LinkedHashMap<>();
        product.getAttributes().forEach((k, v) -> result.put(label(k), value(v)));
        return result;
    }
}
