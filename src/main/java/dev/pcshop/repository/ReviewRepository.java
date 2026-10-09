package dev.pcshop.repository;

import dev.pcshop.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findAllByProductIdOrderByCreatedAtDesc(long productId);

    boolean existsByProductIdAndAccountId(long productId, long accountId);

    /** Пересчитывает среднюю оценку и число отзывов товара после нового отзыва. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE product SET
                rating = (SELECT round(avg(r.rating), 1) FROM review r WHERE r.product_id = :productId),
                reviews_count = (SELECT count(*) FROM review r WHERE r.product_id = :productId)
            WHERE id = :productId""", nativeQuery = true)
    void refreshRating(@Param("productId") long productId);
}
