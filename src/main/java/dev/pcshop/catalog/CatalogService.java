package dev.pcshop.catalog;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final CategoryRepository categories;
    private final ProductRepository products;
    private final ProductSearch search;

    CatalogService(CategoryRepository categories, ProductRepository products, ProductSearch search) {
        this.categories = categories;
        this.products = products;
        this.search = search;
    }

    public List<Category> categories() {
        return categories.findAllByOrderBySortOrder();
    }

    public Category category(String slug) {
        return categories.findBySlug(slug).orElseThrow(() -> new NotFoundException("Категория не найдена: " + slug));
    }

    public Page<Product> search(ProductQuery query) {
        return search.search(category(query.categorySlug()), query);
    }

    /** Поиск по названию и бренду из строки поиска в шапке сайта. */
    public Page<Product> searchText(String text, int page) {
        return search.searchText(text, page, CatalogQueries.PAGE_SIZE);
    }

    public Map<String, List<FacetValue>> facets(String categorySlug) {
        return search.facets(category(categorySlug));
    }

    public Product product(String slug) {
        return products.findBySlugAndActiveTrue(slug).orElseThrow(() -> new NotFoundException("Товар не найден: " + slug));
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

    public List<Product> featured() {
        return products.findTop8ByActiveTrueAndStockGreaterThanOrderByPriceDesc(0);
    }
}
