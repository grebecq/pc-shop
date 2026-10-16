package dev.pcshop.util;

public final class Strings {

    // Транслитерация для адресов страниц: «Мониторы» → monitory
    private static final String[] CYRILLIC = {
            "a", "b", "v", "g", "d", "e", "zh", "z", "i", "y", "k", "l", "m", "n", "o", "p", "r", "s", "t", "u", "f", "h",
            "ts", "ch", "sh", "sch", "", "y", "", "e", "yu", "ya"};

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

    /**
     * Часть адреса страницы из названия: «AMD Ryzen 5 7600» → amd-ryzen-5-7600, «Мониторы» → monitory.
     * Только латиница, цифры и разделитель; пустая строка, если букв и цифр нет совсем.
     */
    public static String slug(String text, char separator) {
        var out = new StringBuilder();
        for (char c : text.toLowerCase().replace('ё', 'е').replace('×', 'x').toCharArray()) {
            if (c >= 'а' && c <= 'я') {
                out.append(CYRILLIC[c - 'а']);
            } else if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                out.append(c);
            } else if (!out.isEmpty() && out.charAt(out.length() - 1) != separator) {
                out.append(separator);
            }
        }
        int end = out.length();
        while (end > 0 && out.charAt(end - 1) == separator) {
            end--;
        }
        return out.substring(0, end);
    }
}
