package dev.pcshop.enums;

import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/** Место в сборке. key — короткое имя для ссылок (/configurator?cpu=1&mb=5), category — slug категории каталога. */
@RequiredArgsConstructor
public enum Slot {
    CPU("cpu", "cpu", "Процессор", true, Set.of("socket", "memory_type", "tdp")),
    MOTHERBOARD("mb", "motherboard", "Материнская плата", true, Set.of("socket", "memory_type", "form_factor")),
    COOLER("cooler", "cooler", "Кулер", false, Set.of("sockets")),
    RAM("ram", "ram", "Оперативная память", true, Set.of("memory_type")),
    GPU("gpu", "gpu", "Видеокарта", false, Set.of("length_mm", "recommended_psu", "tdp")),
    SSD("ssd", "ssd", "Накопитель", true, Set.of("interface")),
    PSU("psu", "psu", "Блок питания", true, Set.of("power")),
    CASE("case", "case", "Корпус", true, Set.of("form_factor", "max_gpu_mm", "max_cooler_mm"));

    private final String key;
    private final String category;
    private final String title;
    /** Без чего компьютер точно не соберётся. Кулер и видеокарта нужны не всегда: это решают проверки. */
    private final boolean required;
    /**
     * Характеристики, без которых не работают проверки совместимости (CompatibilityService).
     * В админке их нельзя удалить из категории или сменить им тип, а у товара они обязательны.
     */
    private final Set<String> specs;

    public String key() { return key; }
    public String category() { return category; }
    public String title() { return title; }
    public boolean required() { return required; }
    public Set<String> specs() { return specs; }

    public static Optional<Slot> byKey(String key) {
        return Arrays.stream(values()).filter(s -> s.key.equals(key)).findFirst();
    }

    /** Слот, в который встаёт товар этой категории. */
    public static Optional<Slot> byCategory(String categorySlug) {
        return Arrays.stream(values()).filter(s -> s.category.equals(categorySlug)).findFirst();
    }

    /** Нужна ли характеристика конфигуратору у товаров этой категории. */
    public static boolean needs(String categorySlug, String specKey) {
        return byCategory(categorySlug).map(s -> s.specs.contains(specKey)).orElse(false);
    }
}
