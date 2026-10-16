package dev.pcshop.controller.api;

import dev.pcshop.dto.response.CategoryResponse;
import dev.pcshop.dto.response.FacetValueResponse;
import dev.pcshop.dto.response.PageResponse;
import dev.pcshop.dto.response.ProductResponse;
import dev.pcshop.mapper.CatalogMapper;
import dev.pcshop.service.CatalogService;
import dev.pcshop.util.CatalogQueries;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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

@Tag(name = "Каталог", description = "Категории, фильтры и товары")
@Validated
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogRestController {

    private final CatalogService catalogService;
    private final CatalogMapper catalogMapper;

    @Operation(summary = "Категории", description = "В порядке меню сайта, с характеристиками для фильтра.")
    @GetMapping("/categories")
    public List<CategoryResponse> getCategories() {
        return catalogMapper.toCategoryResponses(catalogService.categories());
    }

    @Operation(summary = "Значения фильтров категории", description = "Характеристика → значения и сколько товаров с каждым.")
    @GetMapping("/categories/{slug}/facets")
    public Map<String, List<FacetValueResponse>> getFacets(@Parameter(example = "gpu") @PathVariable String slug) {
        return catalogService.facets(slug);
    }

    @Operation(summary = "Поиск товаров",
            description = "Фильтры передаются параметрами с ключами характеристик, список — повтором: "
                    + "?category=cpu&socket=AM5&socket=AM4. Также min, max (цена), stock=1 (в наличии), "
                    + "sort=price_asc|price_desc|name, page (с 0). На странице 12 товаров.")
    @GetMapping("/products")
    public PageResponse<ProductResponse> searchProducts(
            @Parameter(example = "gpu") @RequestParam @NotBlank(message = "Укажите категорию") String category,
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params) {
        var query = CatalogQueries.fromParams(category, params);
        return PageResponse.of(catalogService.search(query).map(catalogMapper::toResponse));
    }

    @Operation(summary = "Товар по slug")
    @GetMapping("/products/{slug}")
    public ProductResponse getProduct(@Parameter(example = "amd-ryzen-7-7800x3d-oem") @PathVariable String slug) {
        return catalogMapper.toResponse(catalogService.product(slug));
    }

    @Operation(summary = "Товары для корзины", description = "Актуальные цены и остатки по id. Снятые с продажи не возвращаются.")
    @GetMapping("/products/by-ids")
    public List<ProductResponse> getProductsByIds(
            @RequestParam
            @NotEmpty(message = "Передайте хотя бы один id")
            @Size(max = 50, message = "Не больше 50 товаров за запрос")
            List<@NotNull(message = "Пустой id товара") @Positive(message = "id товара — число больше 0") Long> ids) {
        return catalogMapper.toProductResponses(catalogService.productsByIds(ids.stream().distinct().toList()));
    }

    @Operation(summary = "Подсказки для строки поиска",
            description = "До 6 товаров, в названии или бренде которых есть все слова запроса. Запрос короче 2 символов — пустой список.")
    @GetMapping("/products/suggest")
    public List<ProductResponse> suggest(
            @Parameter(example = "rtx 5070") @RequestParam @Size(max = 100, message = "Запрос — не длиннее 100 символов") String q) {
        String query = q.strip();
        return query.length() < 2 ? List.of() : catalogMapper.toProductResponses(catalogService.suggest(query, 6));
    }
}
