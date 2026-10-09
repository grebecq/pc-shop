package dev.pcshop.event;

import dev.pcshop.enums.OrderStatus;

/** События заказа. Слушатели (уведомления в Telegram) срабатывают после коммита транзакции. */
public final class OrderEvents {

    private OrderEvents() {
    }

    public record Created(long orderId) {
    }

    public record Paid(long orderId) {
    }

    public record StatusChanged(long orderId, OrderStatus status) {
    }
}
