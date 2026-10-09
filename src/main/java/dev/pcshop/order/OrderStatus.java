package dev.pcshop.order;

public enum OrderStatus {
    AWAITING_PAYMENT("Ждёт оплаты"),
    PAID("Оплачен"),
    ASSEMBLING("Собирается"),
    SHIPPED("Передан в доставку"),
    DONE("Выполнен"),
    CANCELLED("Отменён");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isFinal() {
        return this == DONE || this == CANCELLED;
    }
}
