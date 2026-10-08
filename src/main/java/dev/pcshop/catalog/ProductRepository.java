package dev.pcshop.catalog;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @EntityGraph(attributePaths = "category")
    Optional<Product> findBySlugAndActiveTrue(String slug);

    @EntityGraph(attributePaths = "category")
    List<Product> findAllByIdInAndActiveTrue(Collection<Long> ids);

    @EntityGraph(attributePaths = "category")
    List<Product> findAllByOrderByCategorySortOrderAscNameAsc();

    @EntityGraph(attributePaths = "category")
    List<Product> findTop8ByActiveTrueAndStockGreaterThanOrderByPriceDesc(int stock);

    /**
     * Атомарно списывает остаток. Возвращает 0, если товара не хватает:
     * так два одновременных заказа не уведут остаток в минус.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Product p set p.stock = p.stock - :qty where p.id = :id and p.active = true and p.stock >= :qty")
    int reserve(@Param("id") long id, @Param("qty") int qty);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Product p set p.stock = p.stock + :qty where p.id = :id")
    int release(@Param("id") long id, @Param("qty") int qty);
}
