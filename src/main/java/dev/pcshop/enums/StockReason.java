package dev.pcshop.enums;

import lombok.RequiredArgsConstructor;

// Причина движения остатков
@RequiredArgsConstructor
public enum StockReason {
    RECEIPT("Приход"), WRITE_OFF("Списание"), COUNT("Пересчёт"), SALE("Продажа"), RETURN("Возврат из заказа");

    private final String label;

    public String label() {
        return label;
    }
}
