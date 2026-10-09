package dev.pcshop.dto.request;

import java.util.List;
import java.util.Map;

/**
 * Параметры поиска по каталогу.
 *
 * @param filters характеристика → допустимые значения (внутри ключа ИЛИ, между ключами И)
 */
public record ProductSearchRequest(String categorySlug,
                           Map<String, List<String>> filters,
                           Integer minPrice,
                           Integer maxPrice,
                           boolean inStockOnly,
                           Sort sort,
                           int page,
                           int size) {

    public enum Sort {
        POPULAR, PRICE_ASC, PRICE_DESC, NAME;

        public static Sort parse(String value) {
            if (value == null) {
                return POPULAR;
            }
            return switch (value) {
                case "price_asc" -> PRICE_ASC;
                case "price_desc" -> PRICE_DESC;
                case "name" -> NAME;
                default -> POPULAR;
            };
        }
    }
}
