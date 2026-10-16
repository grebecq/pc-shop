package dev.pcshop.dto.request;

import java.util.List;
import java.util.Map;

public record ProductSearchRequest(String categorySlug,
                           Map<String, List<String>> filters,
                           Integer minPrice,
                           Integer maxPrice,
                           boolean inStockOnly,
                           Sort sort,
                           int page,
                           int size) {

    public enum Sort {
        POPULAR, PRICE_ASC, PRICE_DESC, NAME, RATING;

        public static Sort parse(String value) {
            if (value == null) {
                return POPULAR;
            }
            return switch (value) {
                case "price_asc" -> PRICE_ASC;
                case "price_desc" -> PRICE_DESC;
                case "name" -> NAME;
                case "rating" -> RATING;
                default -> POPULAR;
            };
        }
    }
}
