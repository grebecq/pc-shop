package dev.pcshop.enums;

import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

// Слот сборки
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

    private final boolean required;

    private final Set<String> specs;

    public String key() { return key; }
    public String category() { return category; }
    public String title() { return title; }
    public boolean required() { return required; }
    public Set<String> specs() { return specs; }

    public static Optional<Slot> byKey(String key) {
        return Arrays.stream(values()).filter(s -> s.key.equals(key)).findFirst();
    }

    public static Optional<Slot> byCategory(String categorySlug) {
        return Arrays.stream(values()).filter(s -> s.category.equals(categorySlug)).findFirst();
    }

    public static boolean needs(String categorySlug, String specKey) {
        return byCategory(categorySlug).map(s -> s.specs.contains(specKey)).orElse(false);
    }
}
