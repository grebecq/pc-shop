package dev.pcshop.repository;

import dev.pcshop.entity.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @EntityGraph(attributePaths = "category")
    Optional<Product> findBySlugAndActiveTrue(String slug);

    @EntityGraph(attributePaths = "category")
    List<Product> findAllByIdInAndActiveTrue(Collection<Long> ids);

    @EntityGraph(attributePaths = "category")
    List<Product> findAllByOrderByCategorySortOrderAscNameAsc();

    @EntityGraph(attributePaths = "category")
    List<Product> findAllByCategorySlugAndActiveTrueOrderByPriceAsc(String categorySlug);

    @EntityGraph(attributePaths = "category")
    List<Product> findAllBySlugInAndActiveTrue(Collection<String> slugs);

    @EntityGraph(attributePaths = "category")
    List<Product> findTop8ByActiveTrueAndStockGreaterThanOrderByPriceDesc(int stock);

    /** Адрес товара и дата последней правки для sitemap.xml. */
    record SitemapLink(String slug, Instant updatedAt) {
    }

    /** Все товары на продаже для sitemap.xml: только адрес и дата, без загрузки самих товаров. */
    List<SitemapLink> findSitemapLinksByActiveTrueOrderById();

    /** Товары со скидкой. Их немного, поэтому сортируем по размеру скидки уже в Java. */
    @EntityGraph(attributePaths = "category")
    List<Product> findAllByActiveTrueAndOldPriceNotNull();

    /** Похожие товары: та же категория, в наличии, ближе всего по цене. */
    @Query(value = """
            SELECT p.id FROM product p
            WHERE p.category_id = :categoryId AND p.id <> :productId AND p.active AND p.stock > 0
            ORDER BY abs(p.price - :price), p.id
            LIMIT :limit""", nativeQuery = true)
    List<Long> similarIds(@Param("categoryId") long categoryId, @Param("productId") long productId,
                          @Param("price") int price, @Param("limit") int limit);

    // Остаток меняется только этими запросами и только через StockService, который пишет журнал склада.
    // JPA сам остаток не сохраняет (см. Product.stock), поэтому правка цены в админке не затрёт продажу

    /**
     * Атомарно списывает остаток под заказ. Возвращает 0, если товара не хватает:
     * так два одновременных заказа не уведут остаток в минус.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE product SET stock = stock - :qty WHERE id = :id AND active AND stock >= :qty", nativeQuery = true)
    int reserve(@Param("id") long id, @Param("qty") int qty);

    /** То же без проверки «продаётся»: списать брак можно и у снятого с продажи товара. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE product SET stock = stock - :qty WHERE id = :id AND stock >= :qty", nativeQuery = true)
    int take(@Param("id") long id, @Param("qty") int qty);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE product SET stock = stock + :qty WHERE id = :id", nativeQuery = true)
    int add(@Param("id") long id, @Param("qty") int qty);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE product SET stock = :stock WHERE id = :id", nativeQuery = true)
    int setStock(@Param("id") long id, @Param("stock") int stock);

    /** Остаток с блокировкой строки до конца транзакции: для пересчёта, чтобы продажа не проскочила между чтением и записью. */
    @Query(value = "SELECT stock FROM product WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<Integer> lockStock(@Param("id") long id);

    @Query(value = "SELECT stock FROM product WHERE id = :id", nativeQuery = true)
    int stockOf(@Param("id") long id);

    boolean existsBySlug(String slug);

    @EntityGraph(attributePaths = "category")
    List<Product> findAllByIdIn(Collection<Long> ids);

    long countByCategoryId(long categoryId);
}
