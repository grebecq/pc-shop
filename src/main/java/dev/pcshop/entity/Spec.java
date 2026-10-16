package dev.pcshop.entity;

import dev.pcshop.enums.SpecType;

// Характеристика товара
public record Spec(String key, String label, SpecType type, boolean filter) {
}
