package dev.pcshop.catalog;

import java.util.Comparator;

/** Значение характеристики и сколько товаров с ним — строка фильтра каталога. */
public record FacetValue(String value, int count) {

    /** Числа сортируем как числа (8 < 16 < 32), остальное — по алфавиту. */
    public static final Comparator<FacetValue> ORDER = Comparator
            .comparing((FacetValue f) -> asNumber(f.value()) == null)
            .thenComparing(f -> asNumber(f.value()), Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(FacetValue::value);

    private static Double asNumber(String s) {
        try {
            return Double.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
