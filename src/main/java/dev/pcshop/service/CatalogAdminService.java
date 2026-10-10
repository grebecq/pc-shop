package dev.pcshop.service;

import dev.pcshop.dto.request.CategoryRequest;
import dev.pcshop.dto.request.ProductRequest;
import dev.pcshop.entity.Category;
import dev.pcshop.entity.Product;
import dev.pcshop.entity.Spec;
import dev.pcshop.enums.ProductListFilter;
import dev.pcshop.enums.Slot;
import dev.pcshop.enums.SpecType;
import dev.pcshop.exception.BadRequestException;
import dev.pcshop.exception.ResourceNotFoundException;
import dev.pcshop.repository.CategoryRepository;
import dev.pcshop.repository.ProductRepository;
import dev.pcshop.repository.ProductSearchRepository;
import dev.pcshop.util.Strings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Каталог глазами владельца: заводит и правит товары и категории. Остаток меняет StockService. */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CatalogAdminService {

    public static final int PAGE_SIZE = 50;
    private static final int MAX_TEXT = 128;

    private final CategoryRepository categories;
    private final ProductRepository products;
    private final ProductSearchRepository search;
    private final StockService stock;

    // ---------- Товары ----------

    @Transactional(readOnly = true)
    public Page<Product> products(String text, Long categoryId, ProductListFilter show, int page) {
        return search.adminSearch(Strings.blankToNull(text), categoryId, show, Math.max(page, 0), PAGE_SIZE);
    }

    @Transactional(readOnly = true)
    public Product product(long id) {
        return products.findAllByIdIn(List.of(id)).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Товар не найден"));
    }

    /** Новый товар. Если указан начальный остаток, он оформляется приходом и попадает в журнал склада. */
    public Product create(ProductRequest request) {
        Category category = category(request.categoryId());
        Product product = new Product(category, uniqueSlug(request.name()));
        fill(product, category, request);
        products.save(product);
        if (request.stock() != null && request.stock() > 0) {
            stock.receive(product.getId(), request.stock(), "Начальный остаток");
        }
        log.info("Создан товар {} ({})", product.getName(), product.getSlug());
        return product;
    }

    public Product update(long id, ProductRequest request) {
        Product product = products.findById(id).orElseThrow(() -> new ResourceNotFoundException("Товар не найден"));
        fill(product, category(request.categoryId()), request);
        return product;
    }

    private void fill(Product product, Category category, ProductRequest request) {
        product.edit(category, request.name().strip(), request.brand().strip(),
                request.description() == null ? "" : request.description().strip(), attributes(category, request.specs()));
        product.reprice(request.price(), request.oldPrice());
        product.setActive(request.isActive());
    }

    /**
     * Значения характеристик из формы → JSON товара: числа — числами, галочки — true/false.
     * Сохраняются только характеристики категории. Все ошибки собираются в одно сообщение.
     */
    Map<String, Object> attributes(Category category, Map<String, String> form) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (Spec spec : category.getSpecs()) {
            String raw = Strings.blankToNull(form.get(spec.key()));
            if (spec.type() == SpecType.BOOL) {
                result.put(spec.key(), "true".equals(raw));
                continue;
            }
            if (raw == null) {
                if (Slot.needs(category.getSlug(), spec.key())) {
                    missing.add(spec.label());
                }
                continue;
            }
            if (spec.type() == SpecType.NUMBER) {
                try {
                    // Пробелы внутри числа («1 000») не мешают
                    result.put(spec.key(), Integer.parseInt(raw.replaceAll("\\s", "")));
                } catch (NumberFormatException e) {
                    errors.add("«" + spec.label() + "» — нужно целое число");
                }
            } else if (raw.length() > MAX_TEXT) {
                errors.add("«" + spec.label() + "» длиннее " + MAX_TEXT + " символов");
            } else {
                result.put(spec.key(), raw);
            }
        }
        if (!missing.isEmpty()) {
            errors.addFirst("Конфигуратору сборок нужны: " + String.join(", ", missing));
        }
        if (!errors.isEmpty()) {
            throw new BadRequestException(String.join(". ", errors));
        }
        return result;
    }

    private String uniqueSlug(String name) {
        String base = Strings.slug(name, '-');
        base = base.isEmpty() ? "product" : base.substring(0, Math.min(base.length(), 120));
        String slug = base;
        for (int i = 2; products.existsBySlug(slug); i++) {
            slug = base + "-" + i;
        }
        return slug;
    }

    /**
     * Подсказки для текстовых характеристик: значения, которые уже есть у товаров категории.
     * Так владелец пишет «AM5», а не «am5» или «Socket AM5», и фильтр на сайте не дробится.
     */
    @Transactional(readOnly = true)
    public Map<String, List<String>> suggestions(Category category) {
        return search.specValues(category);
    }

    // ---------- Категории ----------

    @Transactional(readOnly = true)
    public List<Category> categories() {
        return categories.findAllByOrderBySortOrder();
    }

    @Transactional(readOnly = true)
    public Category category(Long id) {
        if (id == null) {
            throw new BadRequestException("Выберите категорию");
        }
        return categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Категория не найдена"));
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> productCounts() {
        return search.countByCategory();
    }

    public Category createCategory(CategoryRequest request) {
        String base = Strings.slug(request.name(), '-');
        base = base.isEmpty() ? "category" : base.substring(0, Math.min(base.length(), 60));
        String slug = base;
        for (int i = 2; categories.findBySlug(slug).isPresent(); i++) {
            slug = base + "-" + i;
        }
        var category = new Category(slug, request.name().strip(), request.sortOrder(), specs(slug, List.of(), request.specs()));
        categories.save(category);
        log.info("Создана категория {} ({})", category.getName(), slug);
        return category;
    }

    public Category updateCategory(long id, CategoryRequest request) {
        Category category = category(id);
        category.update(request.name().strip(), request.sortOrder(), specs(category.getSlug(), category.getSpecs(), request.specs()));
        return category;
    }

    /** Удалить можно только пустую категорию: на товары ссылаются заказы и сборки. */
    public void deleteCategory(long id) {
        Category category = category(id);
        if (products.countByCategoryId(id) > 0) {
            throw new BadRequestException("В категории есть товары. Перенесите их в другую категорию или снимите с продажи");
        }
        if (Slot.byCategory(category.getSlug()).isPresent()) {
            throw new BadRequestException("Эта категория нужна конфигуратору сборок");
        }
        categories.delete(category);
    }

    /**
     * Строки формы → характеристики категории. Ключ существующей характеристики не меняется, у новой придумывается
     * из названия. Характеристики, на которых держится конфигуратор, нельзя удалить или сменить им тип.
     */
    private List<Spec> specs(String categorySlug, List<Spec> current, List<CategoryRequest.SpecRow> rows) {
        Map<String, Spec> byKey = new LinkedHashMap<>();
        current.forEach(s -> byKey.put(s.key(), s));
        Set<String> used = new HashSet<>(byKey.keySet());
        List<Spec> result = new ArrayList<>();
        for (var row : rows) {
            if (row.isBlank()) {
                continue;
            }
            String label = Strings.blankToNull(row.label());
            Spec existing = row.key() == null ? null : byKey.remove(row.key());
            if (existing != null && Slot.needs(categorySlug, existing.key())) {
                if (row.isRemove()) {
                    throw new BadRequestException("«" + existing.label() + "» нужна конфигуратору сборок, её нельзя удалить");
                }
                if (row.type() != existing.type()) {
                    throw new BadRequestException("«" + existing.label() + "» нужна конфигуратору сборок, её тип менять нельзя");
                }
            }
            if (row.isRemove()) {
                continue;
            }
            if (label == null) {
                throw new BadRequestException("У каждой характеристики должно быть название");
            }
            SpecType type = row.type() == null ? SpecType.TEXT : row.type();
            String key = existing != null ? existing.key() : newKey(label, used::contains);
            used.add(key);
            result.add(new Spec(key, label, type, row.isFilter()));
        }
        // Строку существующей характеристики убрали из формы руками — считаем это удалением, но не для нужных конфигуратору
        byKey.values().stream().filter(s -> Slot.needs(categorySlug, s.key())).findFirst().ifPresent(s -> {
            throw new BadRequestException("«" + s.label() + "» нужна конфигуратору сборок, её нельзя удалить");
        });
        return result;
    }

    private static String newKey(String label, Predicate<String> taken) {
        String base = Strings.slug(label, '_');
        base = base.isEmpty() ? "spec" : base.substring(0, Math.min(base.length(), 40));
        String key = base;
        for (int i = 2; taken.test(key); i++) {
            key = base + "_" + i;
        }
        return key;
    }
}
