package dev.pcshop.dto.request;

import dev.pcshop.util.Strings;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Регистрация покупателя на сайте. */
public record RegisterRequest(

        @NotBlank(message = "Как к вам обращаться?")
        @Size(max = 128, message = "Имя — не длиннее 128 символов")
        String name,

        @NotBlank(message = "Укажите email")
        @Email(message = "Проверьте email")
        @Size(max = 255, message = "Email — не длиннее 255 символов")
        String email,

        // Пароль не обрезаем: пробелы по краям — тоже часть пароля
        @NotBlank(message = "Придумайте пароль")
        @Size(min = 8, max = 72, message = "Пароль — от 8 до 72 символов")
        String password,

        // Галочка в форме. Без неё браузер не присылает поле вовсе, поэтому null тоже ошибка
        @NotNull(message = "Нужно согласие на обработку персональных данных")
        @AssertTrue(message = "Нужно согласие на обработку персональных данных")
        Boolean consent) {

    public RegisterRequest {
        name = Strings.strip(name);
        // Пробел, скопированный вместе с адресом, не должен ломать проверку @Email
        email = Strings.strip(email);
    }

    public static RegisterRequest empty() {
        return new RegisterRequest("", "", "", false);
    }
}
