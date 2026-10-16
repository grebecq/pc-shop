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

    record SitemapLink(String slug, Instant updatedAt) {
    }

    List<SitemapLink> findSitemapLinksByActiveTrueOrderById();

    @EntityGraph(attributePaths = "category")
    List<Product> findAllByActiveTrueAndOldPriceNotNull();

    @Query(value = """
            SELECT p.id FROM product p
            WHERE p.category_id = :categoryId AND p.id <> :productId AND p.active AND p.stock > 0
            ORDER BY abs(p.price - :price), p.id
            LIMIT :limit""", nativeQuery = true)
    List<Long> similarIds(@Param("categoryId") long categoryId, @Param("productId") long productId,
                          @Param("price") int price, @Param("limit") int limit);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE product SET stock = stock - :qty WHERE id = :id AND active AND stock >= :qty", nativeQuery = true)
    int reserve(@Param("id") long id, @Param("qty") int qty);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE product SET stock = stock - :qty WHERE id = :id AND stock >= :qty", nativeQuery = true)
    int take(@Param("id") long id, @Param("qty") int qty);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE product SET stock = stock + :qty WHERE id = :id", nativeQuery = true)
    int add(@Param("id") long id, @Param("qty") int qty);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE product SET stock = :stock WHERE id = :id", nativeQuery = true)
    int setStock(@Param("id") long id, @Param("stock") int stock);

    @Query(value = "SELECT stock FROM product WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<Integer> lockStock(@Param("id") long id);

    @Query(value = "SELECT stock FROM product WHERE id = :id", nativeQuery = true)
    int stockOf(@Param("id") long id);

    boolean existsBySlug(String slug);

    @EntityGraph(attributePaths = "category")
    List<Product> findAllByIdIn(Collection<Long> ids);

    long countByCategoryId(long categoryId);
}
