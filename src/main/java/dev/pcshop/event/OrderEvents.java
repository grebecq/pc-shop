package dev.pcshop.event;

import dev.pcshop.enums.OrderStatus;

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
