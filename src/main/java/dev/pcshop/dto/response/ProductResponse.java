package dev.pcshop.dto.response;

import java.util.Map;

public record ProductResponse(long id, String slug, String name, String brand, String category,
                              int price, int stock, String description, String imageUrl, Map<String, Object> attributes) {
}
