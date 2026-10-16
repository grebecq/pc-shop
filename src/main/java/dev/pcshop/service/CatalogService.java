package dev.pcshop.service;

import dev.pcshop.dto.request.ProductSearchRequest;
import dev.pcshop.dto.response.FacetValueResponse;
import dev.pcshop.entity.Category;
import dev.pcshop.entity.Product;
import dev.pcshop.exception.ResourceNotFoundException;
import dev.pcshop.repository.CategoryRepository;
import dev.pcshop.repository.ProductRepository;
import dev.pcshop.repository.ProductSearchRepository;
import dev.pcshop.util.CatalogQueries;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CatalogService {

    private final CategoryRepository categories;
    private final ProductRepository products;
    private final ProductSearchRepository search;

    public List<Category> categories() {
        return categories.findAllByOrderBySortOrder();
    }

    public Category category(String slug) {
        return categories.findBySlug(slug).orElseThrow(() -> new ResourceNotFoundException("Категория не найдена: " + slug));
    }

    public Page<Product> search(ProductSearchRequest query) {
        return search.search(category(query.categorySlug()), query);
    }

    public Page<Product> searchText(String text, int page) {
        return searchText(text, page, CatalogQueries.PAGE_SIZE);
    }

    public Page<Product> searchText(String text, int page, int size) {
        return search.searchText(text, page, size);
    }

    public Map<String, List<FacetValueResponse>> facets(String categorySlug) {
        return search.facets(category(categorySlug));
    }

    public Product product(String slug) {
        return products.findBySlugAndActiveTrue(slug).orElseThrow(() -> new ResourceNotFoundException("Товар не найден: " + slug));
    }

    public List<Product> productsByIds(Collection<Long> ids) {
        return products.findAllByIdInAndActiveTrue(ids);
    }

    public List<Product> productsInOrder(List<Long> ids) {
        Map<Long, Product> byId = products.findAllByIdInAndActiveTrue(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        return ids.stream().distinct().map(byId::get).filter(Objects::nonNull).toList();
    }

    public List<Product> similar(Product product, int limit) {
        return productsInOrder(products.similarIds(product.getCategory().getId(), product.getId(), product.getPrice(), limit));
    }

    public List<Product> suggest(String text, int limit) {
        return search.searchText(text, 0, limit).getContent();
    }

    public List<Product> productsIn(String categorySlug) {
        return products.findAllByCategorySlugAndActiveTrueOrderByPriceAsc(categorySlug);
    }

    public List<Product> productsBySlugs(Collection<String> slugs) {
        return products.findAllBySlugInAndActiveTrue(slugs);
    }

    public List<Product> onSale() {
        return products.findAllByActiveTrueAndOldPriceNotNull().stream()
                .sorted(Comparator.comparingInt(Product::discountPercent).reversed().thenComparing(Product::getPrice))
                .toList();
    }

    public List<Product> featured() {
        return products.findTop8ByActiveTrueAndStockGreaterThanOrderByPriceDesc(0);
    }
}
