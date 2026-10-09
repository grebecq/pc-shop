package dev.pcshop.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Как к вам обращаться?") @Size(max = 128) String name,
        @NotBlank(message = "Укажите email") @Email(message = "Проверьте email") @Size(max = 255) String email,
        @NotBlank(message = "Придумайте пароль") @Size(min = 8, max = 72, message = "Пароль — от 8 до 72 символов") String password) {

    public static RegisterRequest empty() {
        return new RegisterRequest("", "", "");
    }
}
