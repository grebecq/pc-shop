package dev.pcshop.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProfileRequest(
        @NotBlank(message = "Укажите имя") @Size(max = 128) String name,
        @Pattern(regexp = "^$|^[+\\d][\\d\\s()-]{5,30}$", message = "Проверьте номер телефона") String phone) {
}
