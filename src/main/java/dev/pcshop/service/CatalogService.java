package dev.pcshop.service;

import dev.pcshop.dto.request.ProductSearchRequest;
import dev.pcshop.dto.request.ProductUpdateRequest;
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
import java.util.List;
import java.util.Map;

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

    /** Поиск по названию и бренду из строки поиска в шапке сайта. */
    public Page<Product> searchText(String text, int page) {
        return search.searchText(text, page, CatalogQueries.PAGE_SIZE);
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

    /** Все товары категории в продаже, от дешёвых к дорогим (для конфигуратора). */
    public List<Product> productsIn(String categorySlug) {
        return products.findAllByCategorySlugAndActiveTrueOrderByPriceAsc(categorySlug);
    }

    public List<Product> productsBySlugs(Collection<String> slugs) {
        return products.findAllBySlugInAndActiveTrue(slugs);
    }

    /** Все товары, включая снятые с продажи, — для админки. */
    public List<Product> allProducts() {
        return products.findAllByOrderByCategorySortOrderAscNameAsc();
    }

    @Transactional
    public Product updateProduct(long id, ProductUpdateRequest request) {
        Product product = products.findById(id).orElseThrow(() -> new ResourceNotFoundException("Товар не найден"));
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setActive(request.isActive());
        return product;
    }

    public List<Product> featured() {
        return products.findTop8ByActiveTrueAndStockGreaterThanOrderByPriceDesc(0);
    }
}
