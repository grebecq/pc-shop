package dev.pcshop.dto.request;

import dev.pcshop.util.Strings;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Имя и телефон в личном кабинете. */
public record ProfileRequest(

        @NotBlank(message = "Укажите имя")
        @Size(max = 128, message = "Имя — не длиннее 128 символов")
        String name,

        // Телефон необязателен: пустая строка допустима
        @Pattern(regexp = "^$|^[+\\d][\\d\\s()-]{5,30}$", message = "Проверьте номер телефона")
        String phone) {

    public ProfileRequest {
        name = Strings.strip(name);
        phone = Strings.strip(phone);
    }
}
