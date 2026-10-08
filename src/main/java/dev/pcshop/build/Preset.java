package dev.pcshop.build;

/** Готовая сборка для старта: название, для чего она и ссылка на конфигуратор. */
public record Preset(String name, String purpose, Build build) {
}
