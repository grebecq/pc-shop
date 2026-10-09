package dev.pcshop.repository;

import dev.pcshop.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Favorite.Key> {

    /** id товаров в избранном — чтобы закрасить сердечки в карточках. */
    @Query("select f.productId from Favorite f where f.accountId = :accountId")
    Set<Long> productIds(@Param("accountId") long accountId);

    List<Favorite> findAllByAccountIdOrderByCreatedAtDesc(long accountId);

    long countByAccountId(long accountId);
}
