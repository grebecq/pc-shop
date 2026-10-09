package dev.pcshop.enums;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum OrderStatus {
    AWAITING_PAYMENT("Ждёт оплаты"),
    PAID("Оплачен"),
    ASSEMBLING("Собирается"),
    SHIPPED("Передан в доставку"),
    DONE("Выполнен"),
    CANCELLED("Отменён");

    private final String label;

    public String label() {
        return label;
    }

    public boolean isFinal() {
        return this == DONE || this == CANCELLED;
    }
}
