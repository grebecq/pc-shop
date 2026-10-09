package dev.pcshop.dto.response;

import java.util.Comparator;

/** Значение характеристики и сколько товаров с ним — строка фильтра каталога. */
public record FacetValueResponse(String value, int count) {

    /** Числа сортируем как числа (8 < 16 < 32), остальное — по алфавиту. */
    public static final Comparator<FacetValueResponse> ORDER = Comparator
            .comparing((FacetValueResponse f) -> asNumber(f.value()) == null)
            .thenComparing(f -> asNumber(f.value()), Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(FacetValueResponse::value);

    private static Double asNumber(String s) {
        try {
            return Double.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
