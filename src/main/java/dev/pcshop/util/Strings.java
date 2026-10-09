package dev.pcshop.util;

public final class Strings {

    private Strings() {
    }

    /** Пробелы по краям убираем, null оставляем null — для полей форм и запросов. */
    public static String strip(String s) {
        return s == null ? null : s.strip();
    }

    /** Пустую строку из формы храним как null, остальное — без пробелов по краям. */
    public static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
