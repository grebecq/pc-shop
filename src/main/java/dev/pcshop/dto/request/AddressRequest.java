package dev.pcshop.dto.request;

import dev.pcshop.util.Strings;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Адрес доставки из формы личного кабинета или оформления заказа. */
public record AddressRequest(

        @NotBlank(message = "Назовите адрес, например «Дом»")
        @Size(max = 64, message = "Название — не длиннее 64 символов")
        String label,

        @NotBlank(message = "Укажите город")
        @Size(max = 128, message = "Город — не длиннее 128 символов")
        String city,

        @NotBlank(message = "Укажите улицу и дом")
        @Size(max = 256, message = "Улица и дом — не длиннее 256 символов")
        String street,

        @Size(max = 32, message = "Квартира — не длиннее 32 символов")
        String apartment,

        @Pattern(regexp = "^$|^\\d{6}$", message = "Индекс — 6 цифр")
        String postalCode,

        Boolean makeDefault) {

    public AddressRequest {
        label = Strings.strip(label);
        city = Strings.strip(city);
        street = Strings.strip(street);
        apartment = Strings.strip(apartment);
        postalCode = Strings.strip(postalCode);
    }

    public static AddressRequest empty() {
        return new AddressRequest("Дом", "", "", "", "", false);
    }

    /** Чекбокс не приходит в запросе, если не отмечен, поэтому null = «нет». */
    public boolean wantsDefault() {
        return Boolean.TRUE.equals(makeDefault);
    }
}
