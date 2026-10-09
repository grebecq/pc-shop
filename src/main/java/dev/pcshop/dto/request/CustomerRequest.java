package dev.pcshop.dto.request;

import dev.pcshop.util.Strings;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Контакты покупателя в заказе. Одни правила для сайта и для бота. */
public record CustomerRequest(

        @NotBlank(message = "Укажите имя")
        @Size(max = 128, message = "Имя — не длиннее 128 символов")
        String name,

        @NotBlank(message = "Укажите телефон")
        @Pattern(regexp = "^[+0-9 ()-]{6,32}$", message = "Телефон в формате +7 900 000-00-00")
        String phone,

        @NotBlank(message = "Укажите адрес доставки")
        @Size(min = 5, max = 512, message = "Адрес — от 5 до 512 символов")
        String address,

        @Size(max = 1000, message = "Комментарий — не длиннее 1000 символов")
        String comment,

        // Необязательный: туда придут письма о заказе. В боте его не спрашиваем
        @Email(message = "Проверьте email")
        @Size(max = 254, message = "Email — не длиннее 254 символов")
        String email) {

    public CustomerRequest {
        name = Strings.strip(name);
        phone = Strings.strip(phone);
        address = Strings.strip(address);
        comment = Strings.blankToNull(comment);
        email = Strings.blankToNull(email);
    }

    /** Без email — так заказывают в боте. */
    public CustomerRequest(String name, String phone, String address, String comment) {
        this(name, phone, address, comment, null);
    }

    public CustomerRequest withAddressAndEmail(String newAddress, String newEmail) {
        return new CustomerRequest(name, phone, newAddress, comment, newEmail);
    }
}
