package dev.pcshop.dto.request;

import dev.pcshop.util.Strings;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(

        String token,

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

    @AssertTrue(message = "Пароли не совпадают")
    public boolean isConfirmed() {
        return confirm == null || confirm.isBlank() || confirm.equals(password);
    }
}
