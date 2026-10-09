package dev.pcshop.exception;

import java.time.LocalDateTime;

/** Тело ошибки REST API: короткий код и сообщение, которое можно показать покупателю. */
public record ErrorResponseDto(String error, String message, LocalDateTime timestamp) {
}
