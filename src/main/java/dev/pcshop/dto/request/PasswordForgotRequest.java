package dev.pcshop.dto.request;

import dev.pcshop.util.Strings;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** «Забыли пароль?»: email, на который отправить ссылку для смены пароля. */
public record PasswordForgotRequest(

        @NotBlank(message = "Укажите email")
        @Email(message = "Проверьте email")
        @Size(max = 255, message = "Email — не длиннее 255 символов")
        String email) {

    public PasswordForgotRequest {
        email = Strings.strip(email);
    }

    public static PasswordForgotRequest empty() {
        return new PasswordForgotRequest("");
    }
}
