package dev.pcshop.dto.response;

import dev.pcshop.model.Build;

// Готовая сборка
public record PresetResponse(String name, String purpose, Build build) {
}
