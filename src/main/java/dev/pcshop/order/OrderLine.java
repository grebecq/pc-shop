package dev.pcshop.order;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record OrderLine(long productId, @Min(value = 1, message = "Количество — от 1 до 20") @Max(value = 20, message = "Количество — от 1 до 20") int quantity) {
}
