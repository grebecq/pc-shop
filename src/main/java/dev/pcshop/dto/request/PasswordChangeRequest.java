package dev.pcshop.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Смена пароля в личном кабинете. */
public record PasswordChangeRequest(
        @NotBlank(message = "Введите текущий пароль") String current,
        @NotBlank(message = "Придумайте новый пароль")
        @Size(min = 8, max = 72, message = "Новый пароль — от 8 до 72 символов") String password) {
}
