package dev.pcshop.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Адрес доставки из формы личного кабинета или оформления заказа. */
public record AddressForm(
        @NotBlank(message = "Назовите адрес, например «Дом»") @Size(max = 64) String label,
        @NotBlank(message = "Укажите город") @Size(max = 128) String city,
        @NotBlank(message = "Укажите улицу и дом") @Size(max = 256) String street,
        @Size(max = 32) String apartment,
        @Pattern(regexp = "^$|^\\d{6}$", message = "Индекс — 6 цифр") String postalCode,
        Boolean makeDefault) {

    public static AddressForm empty() {
        return new AddressForm("Дом", "", "", "", "", false);
    }

    /** Чекбокс не приходит в запросе, если не отмечен, поэтому null = «нет». */
    public boolean wantsDefault() {
        return Boolean.TRUE.equals(makeDefault);
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
