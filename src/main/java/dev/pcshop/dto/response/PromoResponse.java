package dev.pcshop.dto.response;

public record PromoResponse(String code, Integer percent, Integer amount, int minTotal, String title) {
}
