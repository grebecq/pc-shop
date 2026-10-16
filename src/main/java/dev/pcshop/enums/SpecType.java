package dev.pcshop.enums;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum SpecType {
    NUMBER("Число"), TEXT("Текст"), BOOL("Да / нет");

    private final String label;

    public String label() {
        return label;
    }
}
