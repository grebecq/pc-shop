package dev.pcshop.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Поиск по каталогу с фильтрами по JSONB-характеристикам.
 * SQL собирается динамически, но ключи характеристик берутся только из белого списка категории,
 * а значения всегда идут параметрами, поэтому инъекция невозможна.
 */
@Component
class ProductSearch {

    private final NamedParameterJdbcTemplate jdbc;
    private final ProductRepository products;

    ProductSearch(NamedParameterJdbcTemplate jdbc, ProductRepository products) {
        this.jdbc = jdbc;
        this.products = products;
    }

    Page<Product> search(Category category, ProductQuery query) {
        Set<String> allowedKeys = category.getFilters().stream().map(FilterDef::key).collect(Collectors.toSet());
        var params = new MapSqlParameterSource("categoryId", category.getId());
        var where = new StringBuilder("p.category_id = :categoryId AND p.active");

        int i = 0;
        for (var entry : query.filters().entrySet()) {
            if (!allowedKeys.contains(entry.getKey()) || entry.getValue().isEmpty()) {
                continue;
            }
            where.append(" AND p.attributes ->> :key").append(i).append(" IN (:values").append(i).append(')');
            params.addValue("key" + i, entry.getKey());
            params.addValue("values" + i, entry.getValue());
            i++;
        }
        if (query.minPrice() != null) {
            where.append(" AND p.price >= :minPrice");
            params.addValue("minPrice", query.minPrice());
        }
        if (query.maxPrice() != null) {
            where.append(" AND p.price <= :maxPrice");
            params.addValue("maxPrice", query.maxPrice());
        }
        if (query.inStockOnly()) {
            where.append(" AND p.stock > 0");
        }

        String order = switch (query.sort()) {
            case PRICE_ASC -> "p.price ASC";
            case PRICE_DESC -> "p.price DESC";
            case NAME -> "p.name ASC";
            // «Популярные»: сначала то, что есть в наличии, затем по остатку
            case POPULAR -> "(p.stock > 0) DESC, p.stock DESC, p.name ASC";
        };

        long total = jdbc.queryForObject("SELECT count(*) FROM product p WHERE " + where, params, Long.class);
        params.addValue("limit", query.size()).addValue("offset", (long) query.page() * query.size());
        List<Long> ids = jdbc.queryForList(
                "SELECT p.id FROM product p WHERE " + where + " ORDER BY " + order + ", p.id LIMIT :limit OFFSET :offset",
                params, Long.class);

        // Загружаем сущности одним запросом и восстанавливаем порядок сортировки
        Map<Long, Product> byId = products.findAllByIdInAndActiveTrue(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<Product> page = ids.stream().map(byId::get).toList();
        return new PageImpl<>(page, PageRequest.of(query.page(), query.size()), total);
    }

    /**
     * Каждое слово запроса должно встретиться в названии или бренде: «rtx 5070 asus».
     * Спецсимволы LIKE экранируем, чтобы «%» в запросе не превращался в шаблон.
     */
    Page<Product> searchText(String text, int page, int size) {
        String[] words = text.toLowerCase().split("\\s+");
        var params = new MapSqlParameterSource();
        var where = new StringBuilder("p.active");
        int n = 0;
        for (String word : words) {
            if (word.isBlank() || n == 6) {
                continue;
            }
            String like = "%" + word.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            where.append(" AND (lower(p.name) LIKE :w").append(n).append(" OR lower(p.brand) LIKE :w").append(n).append(')');
            params.addValue("w" + n, like);
            n++;
        }
        long total = jdbc.queryForObject("SELECT count(*) FROM product p WHERE " + where, params, Long.class);
        params.addValue("limit", size).addValue("offset", (long) page * size);
        List<Long> ids = jdbc.queryForList("SELECT p.id FROM product p WHERE " + where
                + " ORDER BY (p.stock > 0) DESC, p.price DESC, p.id LIMIT :limit OFFSET :offset", params, Long.class);
        Map<Long, Product> byId = products.findAllByIdInAndActiveTrue(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        return new PageImpl<>(ids.stream().map(byId::get).toList(), PageRequest.of(page, size), total);
    }

    /** Значения каждой характеристики категории с количеством товаров — для фильтра. */
    Map<String, List<FacetValue>> facets(Category category) {
        Map<String, List<FacetValue>> result = new LinkedHashMap<>();
        for (FilterDef filter : category.getFilters()) {
            var params = new MapSqlParameterSource("categoryId", category.getId()).addValue("key", filter.key());
            List<FacetValue> values = new ArrayList<>(jdbc.query("""
                            SELECT p.attributes ->> :key AS value, count(*) AS cnt
                            FROM product p
                            WHERE p.category_id = :categoryId AND p.active AND p.attributes ->> :key IS NOT NULL
                            GROUP BY 1""",
                    params, (rs, n) -> new FacetValue(rs.getString("value"), rs.getInt("cnt"))));
            values.sort(FacetValue.ORDER);
            result.put(filter.key(), values);
        }
        return result;
    }
}
