package dev.pcshop.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/** Цена, остаток и видимость товара из админки. */
public record ProductUpdateRequest(
        @NotNull(message = "Укажите цену") @Positive(message = "Цена должна быть больше нуля")
        @Max(value = 10_000_000, message = "Слишком большая цена") Integer price,
        @NotNull(message = "Укажите остаток") @PositiveOrZero(message = "Остаток не может быть отрицательным")
        @Max(value = 100_000, message = "Слишком большой остаток") Integer stock,
        Boolean active) {

    /** Снятая галочка в запрос не приходит, поэтому null = «не продаётся». */
    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }
}
