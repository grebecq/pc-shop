package dev.pcshop.dto.response;

import java.math.BigDecimal;
import java.util.Map;

/** @param rating средняя оценка из отзывов, null — отзывов нет */
public record ProductResponse(long id, String slug, String name, String brand, String category,
                              int price, int stock, String description, String imageUrl, Map<String, Object> attributes,
                              BigDecimal rating, int reviewsCount) {
}
