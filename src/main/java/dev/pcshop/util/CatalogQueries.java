package dev.pcshop.util;

import dev.pcshop.dto.request.ProductSearchRequest;
import org.springframework.util.MultiValueMap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Разбор параметров запроса каталога — общий для API и страниц сайта. */
public final class CatalogQueries {

    public static final int PAGE_SIZE = 12;
    private static final Set<String> RESERVED = Set.of("category", "sort", "page", "min", "max", "stock");

    private CatalogQueries() {
    }

    public static ProductSearchRequest fromParams(String category, MultiValueMap<String, String> params) {
        Map<String, List<String>> filters = new LinkedHashMap<>();
        params.forEach((key, values) -> {
            if (!RESERVED.contains(key)) {
                List<String> clean = values.stream().filter(v -> v != null && !v.isBlank()).toList();
                if (!clean.isEmpty()) {
                    filters.put(key, clean);
                }
            }
        });
        return new ProductSearchRequest(
                category,
                filters,
                parseInt(params.getFirst("min")),
                parseInt(params.getFirst("max")),
                "1".equals(params.getFirst("stock")),
                ProductSearchRequest.Sort.parse(params.getFirst("sort")),
                Math.max(0, parseIntOr(params.getFirst("page"), 0)),
                PAGE_SIZE);
    }

    private static Integer parseInt(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(s.replaceAll("\\s", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int parseIntOr(String s, int fallback) {
        Integer v = parseInt(s);
        return v == null ? fallback : v;
    }
}
