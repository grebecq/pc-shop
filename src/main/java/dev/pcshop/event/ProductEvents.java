package dev.pcshop.event;

public final class ProductEvents {

    private ProductEvents() {
    }

    public record Restocked(long productId) {
    }
}
