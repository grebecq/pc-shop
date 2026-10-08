package dev.pcshop.catalog;

import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** REST API каталога: его используют корзина на сайте и может использовать любой внешний клиент. */
@RestController
@RequestMapping("/api")
class CatalogApi {

    private final CatalogService catalog;

    CatalogApi(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/categories")
    List<CategoryDto> categories() {
        return catalog.categories().stream().map(CategoryDto::of).toList();
    }

    @GetMapping("/categories/{slug}/facets")
    Map<String, List<FacetValue>> facets(@PathVariable String slug) {
        return catalog.facets(slug);
    }

    /** Пример: /api/products?category=cpu&socket=AM5&socket=AM4&sort=price_asc */
    @GetMapping("/products")
    PageDto<ProductDto> products(@RequestParam String category,
                                 @RequestParam MultiValueMap<String, String> all) {
        var query = CatalogQueries.fromParams(category, all);
        var page = catalog.search(query);
        return new PageDto<>(page.map(ProductDto::of).getContent(), page.getNumber(), page.getTotalPages(), page.getTotalElements());
    }

    @GetMapping("/products/{slug}")
    ProductDto product(@PathVariable String slug) {
        return ProductDto.of(catalog.product(slug));
    }

    /** Данные для корзины: цены и остатки берутся с сервера, а не из браузера. */
    @GetMapping("/products/by-ids")
    List<ProductDto> byIds(@RequestParam List<Long> ids) {
        return catalog.productsByIds(ids).stream().map(ProductDto::of).toList();
    }

    record CategoryDto(String slug, String name, List<FilterDef> filters) {
        static CategoryDto of(Category c) {
            return new CategoryDto(c.getSlug(), c.getName(), c.getFilters());
        }
    }

    record ProductDto(long id, String slug, String name, String brand, String category,
                      int price, int stock, String description, Map<String, Object> attributes) {
        static ProductDto of(Product p) {
            return new ProductDto(p.getId(), p.getSlug(), p.getName(), p.getBrand(), p.getCategory().getSlug(),
                    p.getPrice(), p.getStock(), p.getDescription(), p.getAttributes());
        }
    }

    record PageDto<T>(List<T> items, int page, int totalPages, long total) {
    }
}
