package dev.pcshop.enums;

import lombok.RequiredArgsConstructor;

/** Какие товары показать в списке админки. */
@RequiredArgsConstructor
public enum ProductListFilter {
    ALL("Все"), ACTIVE("В продаже"), OUT_OF_STOCK("Закончились"), HIDDEN("Сняты с продажи");

    private final String label;

    public String label() {
        return label;
    }
}
