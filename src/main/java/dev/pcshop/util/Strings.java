package dev.pcshop.util;

public final class Strings {

    private Strings() {
    }

    /** Пустую строку из формы храним как null, остальное — без пробелов по краям. */
    public static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
