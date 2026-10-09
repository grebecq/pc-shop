package dev.pcshop.dto.request;

import dev.pcshop.util.Strings;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Новый пароль по ссылке из письма. Токен приходит из скрытого поля формы. */
public record PasswordResetRequest(

        String token,

        // Пароль не обрезаем: пробелы по краям — тоже часть пароля
        @NotBlank(message = "Придумайте пароль")
        @Size(min = 8, max = 72, message = "Пароль — от 8 до 72 символов")
        String password,

        @NotBlank(message = "Повторите пароль")
        String confirm) {

    public PasswordResetRequest {
        token = Strings.strip(token);
    }

    public static PasswordResetRequest forToken(String token) {
        return new PasswordResetRequest(token, "", "");
    }

    /** Повтор совпадает с паролем. Пустой повтор уже отмечен @NotBlank, второй раз о нём не пишем. */
    @AssertTrue(message = "Пароли не совпадают")
    public boolean isConfirmed() {
        return confirm == null || confirm.isBlank() || confirm.equals(password);
    }
}
