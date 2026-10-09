package dev.pcshop.dto.response;

import dev.pcshop.model.Build;

/** Готовая сборка для старта: название, для чего она и ссылка на конфигуратор. */
public record PresetResponse(String name, String purpose, Build build) {
}
