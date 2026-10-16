package dev.pcshop.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

// Строка заказа
public record OrderLineRequest(

        @Positive(message = "Неизвестный товар")
        long productId,

        @Min(value = 1, message = "Количество — от 1 до 20")
        @Max(value = 20, message = "Количество — от 1 до 20")
        int quantity) {
}
