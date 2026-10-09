package dev.pcshop.dto.response;

public record OrderItemResponse(long productId, String name, int price, int quantity) {
}
