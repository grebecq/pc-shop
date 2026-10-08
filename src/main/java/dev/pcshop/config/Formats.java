package dev.pcshop.config;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Форматирование цен и дат. Доступно в шаблонах как ${@fmt.price(...)}. */
@Component("fmt")
public class Formats {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZONE);

    /** 17490 → «17 490 ₽» */
    public static String price(long rubles) {
        return String.format("%,d", rubles).replace(',', ' ') + " ₽";
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
}
