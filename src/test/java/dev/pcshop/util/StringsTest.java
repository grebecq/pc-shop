package dev.pcshop.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StringsTest {

    @Test
    void slugTransliteratesAndCleans() {
        assertThat(Strings.slug("AMD Ryzen 5 7600 (OEM)", '-')).isEqualTo("amd-ryzen-5-7600-oem");
        assertThat(Strings.slug("Мониторы и ТВ", '-')).isEqualTo("monitory-i-tv");
        assertThat(Strings.slug("Частота, Гц", '_')).isEqualTo("chastota_gts");
        assertThat(Strings.slug("Ёмкость 2×16", '-')).isEqualTo("emkost-2x16");
        assertThat(Strings.slug("  «Съёмный» —  ", '-')).isEqualTo("semnyy");
        assertThat(Strings.slug("%%%", '-')).isEmpty();
    }
}
