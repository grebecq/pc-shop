package dev.pcshop.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProfileForm(
        @NotBlank(message = "Укажите имя") @Size(max = 128) String name,
        @Pattern(regexp = "^$|^[+\\d][\\d\\s()-]{5,30}$", message = "Проверьте номер телефона") String phone) {
}
