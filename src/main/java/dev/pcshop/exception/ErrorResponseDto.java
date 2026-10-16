package dev.pcshop.exception;

import java.time.LocalDateTime;

public record ErrorResponseDto(String error, String message, LocalDateTime timestamp) {
}
