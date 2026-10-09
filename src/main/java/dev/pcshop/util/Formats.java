package dev.pcshop.util;

import dev.pcshop.entity.Product;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.of("ru")).withZone(ZONE);
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

    /** «9 октября 2026» — для отзывов. */
    public String day(Instant instant) {
        return instant == null ? "" : DAY.format(instant);
    }

    public static String timeOfDay(Instant instant) {
        return instant == null ? "" : TIME.format(instant);
    }

    public String time(Instant instant) {
        return timeOfDay(instant);
    }

    /** Оценка как принято в русских магазинах: 4.7 → «4,7». */
    public static String ratingText(BigDecimal rating) {
        return rating == null ? "" : rating.setScale(1, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    public String rating(BigDecimal rating) {
        return ratingText(rating);
    }

    /** 1 отзыв, 3 отзыва, 11 отзывов. */
    public String reviews(int count) {
        int mod100 = count % 100;
        int mod10 = count % 10;
        String word = mod100 >= 11 && mod100 <= 14 ? "отзывов"
                : mod10 == 1 ? "отзыв"
                : mod10 >= 2 && mod10 <= 4 ? "отзыва"
                : "отзывов";
        return count + " " + word;
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
