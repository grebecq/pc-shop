package dev.pcshop.config;

import dev.pcshop.catalog.Attributes;
import dev.pcshop.catalog.Product;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** Форматирование цен и дат. Доступно в шаблонах как ${@fmt.price(...)}. */
@Component("fmt")
public class Formats {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZONE);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZONE);

    /** 17490 → «17 490 ₽» */
    public static String price(long rubles) {
        // Locale.ROOT: иначе на сервере с русской локалью разделитель будет другим и замена не сработает
        return String.format(java.util.Locale.ROOT, "%,d", rubles).replace(',', ' ') + " ₽";
    }

    public static String dateTime(Instant instant) {
        return instant == null ? "" : DATE_TIME.format(instant);
    }

    public String rub(long rubles) {
        return price(rubles);
    }

    public String date(Instant instant) {
        return dateTime(instant);
    }

    public static String timeOfDay(Instant instant) {
        return instant == null ? "" : TIME.format(instant);
    }

    public String time(Instant instant) {
        return timeOfDay(instant);
    }

    /** Короткая строка характеристик для карточки: «AM5 · 6 · 12». */
    public String specs(Product product) {
        return Attributes.brief(product);
    }

    /** Отмечен ли чекбокс фильтра. */
    public boolean selected(Map<String, List<String>> selected, String key, String value) {
        return selected.getOrDefault(key, List.of()).contains(value);
    }
}
