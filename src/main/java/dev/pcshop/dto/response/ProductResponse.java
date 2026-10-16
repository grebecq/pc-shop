package dev.pcshop.dto.response;

import java.math.BigDecimal;
import java.util.Map;

public record ProductResponse(long id, String slug, String name, String brand, String category,
                              int price, Integer oldPrice, int stock, String description, String imageUrl, Map<String, Object> attributes,
                              BigDecimal rating, int reviewsCount) {
}
