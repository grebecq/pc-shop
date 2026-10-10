package dev.pcshop.enums;

import lombok.RequiredArgsConstructor;

/** Тип значения характеристики: от него зависят поле формы и то, как значение хранится в JSON. */
@RequiredArgsConstructor
public enum SpecType {
    NUMBER("Число"), TEXT("Текст"), BOOL("Да / нет");

    private final String label;

    public String label() {
        return label;
    }
}
