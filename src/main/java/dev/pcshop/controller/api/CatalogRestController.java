package dev.pcshop.controller.api;

import dev.pcshop.dto.response.CategoryResponse;
import dev.pcshop.dto.response.FacetValueResponse;
import dev.pcshop.dto.response.PageResponse;
import dev.pcshop.dto.response.ProductResponse;
import dev.pcshop.mapper.CatalogMapper;
import dev.pcshop.service.CatalogService;
import dev.pcshop.util.CatalogQueries;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** REST API каталога: его используют корзина на сайте и может использовать любой внешний клиент. */
@Validated
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogRestController {

    private final CatalogService catalogService;
    private final CatalogMapper catalogMapper;

    @GetMapping("/categories")
    public List<CategoryResponse> getCategories() {
        return catalogMapper.toCategoryResponses(catalogService.categories());
    }

    @GetMapping("/categories/{slug}/facets")
    public Map<String, List<FacetValueResponse>> getFacets(@PathVariable String slug) {
        return catalogService.facets(slug);
    }

    /** Пример: /api/products?category=cpu&socket=AM5&socket=AM4&sort=price_asc */
    @GetMapping("/products")
    public PageResponse<ProductResponse> searchProducts(@RequestParam @NotBlank String category,
                                                       @RequestParam MultiValueMap<String, String> params) {
        var query = CatalogQueries.fromParams(category, params);
        return PageResponse.of(catalogService.search(query).map(catalogMapper::toResponse));
    }

    @GetMapping("/products/{slug}")
    public ProductResponse getProduct(@PathVariable String slug) {
        return catalogMapper.toResponse(catalogService.product(slug));
    }

    /** Данные для корзины: цены и остатки берутся с сервера, а не из браузера. */
    @GetMapping("/products/by-ids")
    public List<ProductResponse> getProductsByIds(
            // Корзина больше 50 позиций не бывает; ограничение защищает от огромных IN-запросов
            @RequestParam @NotEmpty @Size(max = 50, message = "Не больше 50 товаров за запрос") List<Long> ids) {
        return catalogMapper.toProductResponses(catalogService.productsByIds(ids.stream().distinct().toList()));
    }
}
