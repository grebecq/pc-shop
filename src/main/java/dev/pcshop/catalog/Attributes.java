package dev.pcshop.catalog;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
            Map.entry("color", "Цвет"),
            Map.entry("igpu", "Встроенная графика"),
            Map.entry("box_cooler", "Кулер в комплекте"),
            Map.entry("m2_gen5", "M.2 с PCIe 5.0"),
            Map.entry("max_cooler_mm", "Макс. высота кулера, мм"),
            Map.entry("max_radiator_mm", "Макс. радиатор СЖО, мм"),
            Map.entry("cooler_type", "Тип"),
            Map.entry("sockets", "Сокеты"),
            Map.entry("height_mm", "Высота, мм"),
            Map.entry("radiator_mm", "Радиатор, мм"),
            Map.entry("max_tdp", "Рассеивает до, Вт"),
            Map.entry("gpu_chip", "Видеочип"),
            Map.entry("vram_type", "Тип видеопамяти"),
            Map.entry("boost_mhz", "Частота в бусте, МГц"),
            Map.entry("slots", "Толщина, слотов"),
            Map.entry("boost_ghz", "Частота в бусте, ГГц"),
            Map.entry("wifi", "Wi-Fi"),
            Map.entry("cl", "Задержка CL"),
            Map.entry("atx3", "ATX 3.x с разъёмом 12V-2x6"));

    // JSONB не хранит порядок ключей, поэтому порядок вывода задаём сами: важное сначала
    private static final List<String> ORDER = List.of(
            "socket", "gpu_chip", "memory_gb", "vram_type", "chip_vendor", "boost_mhz", "cores", "boost_ghz", "threads",
            "chipset", "memory_type", "capacity_gb", "modules", "frequency", "cl", "form_factor", "interface", "read_mbps", "power", "certificate", "modular",
            "cooler_type", "max_tdp", "height_mm", "radiator_mm", "sockets",
            "max_gpu_mm", "max_cooler_mm", "max_radiator_mm", "length_mm", "slots", "recommended_psu", "tdp", "color",
            "igpu", "box_cooler", "m2_gen5", "wifi", "atx3");

    // Короткая запись значения с единицами для карточек товара
    private static final Map<String, String> SHORT = Map.ofEntries(
            Map.entry("cores", "%s ядер"),
            Map.entry("threads", "%s потоков"),
            Map.entry("tdp", "%s Вт"),
            Map.entry("capacity_gb", "%s ГБ"),
            Map.entry("modules", "%s ГБ"),
            Map.entry("frequency", "%s МГц"),
            Map.entry("memory_gb", "%s ГБ"),
            Map.entry("length_mm", "%s мм"),
            Map.entry("recommended_psu", "БП от %s Вт"),
            Map.entry("read_mbps", "до %s МБ/с"),
            Map.entry("power", "%s Вт"),
            Map.entry("max_gpu_mm", "GPU до %s мм"),
            Map.entry("max_tdp", "до %s Вт"),
            Map.entry("max_cooler_mm", "кулер до %s мм"),
            Map.entry("max_radiator_mm", "СЖО до %s мм"),
            Map.entry("height_mm", "%s мм"),
            Map.entry("radiator_mm", "радиатор %s мм"),
            Map.entry("boost_ghz", "до %s ГГц"),
            Map.entry("boost_mhz", "до %s МГц"),
            Map.entry("cl", "CL%s"));

    private static final Comparator<String> BY_ORDER = Comparator.comparingInt(k -> {
        int i = ORDER.indexOf(k);
        return i < 0 ? ORDER.size() : i;
    });

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
        product.getAttributes().keySet().stream().sorted(BY_ORDER)
                .forEach(k -> result.put(label(k), value(product.getAttributes().get(k))));
        return result;
    }

    /** Три главные характеристики одной строкой: «AM5 · 6 ядер · 65 Вт». */
    public static String brief(Product product) {
        return product.getAttributes().entrySet().stream()
                .filter(e -> !(e.getValue() instanceof Boolean))
                .sorted(Map.Entry.comparingByKey(BY_ORDER))
                .limit(3)
                .map(e -> SHORT.containsKey(e.getKey())
                        ? SHORT.get(e.getKey()).formatted(value(e.getValue()))
                        : value(e.getValue()))
                .collect(Collectors.joining(" · "));
    }
}
