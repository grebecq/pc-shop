package dev.pcshop.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerRequest(
        @NotBlank(message = "Укажите имя") @Size(max = 128) String name,
        @NotBlank(message = "Укажите телефон")
        @Pattern(regexp = "^[+0-9 ()-]{6,32}$", message = "Телефон в формате +7 900 000-00-00") String phone,
        @NotBlank(message = "Укажите адрес доставки") @Size(max = 512) String address,
        @Size(max = 1000) String comment) {
}
