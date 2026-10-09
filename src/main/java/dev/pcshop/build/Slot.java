package dev.pcshop.build;

import java.util.Arrays;
import java.util.Optional;

/** Место в сборке. key — короткое имя для ссылок (/configurator?cpu=1&mb=5), category — slug категории каталога. */
public enum Slot {
    CPU("cpu", "cpu", "Процессор", true),
    MOTHERBOARD("mb", "motherboard", "Материнская плата", true),
    COOLER("cooler", "cooler", "Кулер", false),
    RAM("ram", "ram", "Оперативная память", true),
    GPU("gpu", "gpu", "Видеокарта", false),
    SSD("ssd", "ssd", "Накопитель", true),
    PSU("psu", "psu", "Блок питания", true),
    CASE("case", "case", "Корпус", true);

    private final String key;
    private final String category;
    private final String title;
    /** Без чего компьютер точно не соберётся. Кулер и видеокарта нужны не всегда: это решают проверки. */
    private final boolean required;

    Slot(String key, String category, String title, boolean required) {
        this.key = key;
        this.category = category;
        this.title = title;
        this.required = required;
    }

    public String key() { return key; }
    public String category() { return category; }
    public String title() { return title; }
    public boolean required() { return required; }

    public static Optional<Slot> byKey(String key) {
        return Arrays.stream(values()).filter(s -> s.key.equals(key)).findFirst();
    }
}
