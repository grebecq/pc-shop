package dev.pcshop.dto.response;

import dev.pcshop.enums.DeliveryMethod;
import dev.pcshop.enums.OrderStatus;

import java.util.List;
import java.util.UUID;

public record OrderResponse(UUID publicId, String number, OrderStatus status, String statusLabel,
                            DeliveryMethod deliveryMethod, int deliveryPrice, String promoCode, int discount, int total,
                            List<OrderItemResponse> items) {
}
