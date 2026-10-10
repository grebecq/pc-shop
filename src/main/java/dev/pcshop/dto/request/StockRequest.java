package dev.pcshop.dto.request;

import dev.pcshop.enums.StockReason;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Операция со складом из карточки товара.
 *
 * @param operation RECEIPT, WRITE_OFF или COUNT; продажи и возвраты делает сам магазин
 * @param quantity  сколько пришло или списано, а для пересчёта — сколько есть на самом деле
 */
public record StockRequest(
        @NotNull(message = "Выберите операцию") StockReason operation,
        @NotNull(message = "Укажите количество") @PositiveOrZero(message = "Количество не может быть отрицательным")
        @Max(value = 100_000, message = "Слишком большое количество") Integer quantity,
        @Size(max = 256, message = "Комментарий длиннее 256 символов") String comment) {
}
