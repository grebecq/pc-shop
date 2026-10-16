package dev.pcshop.repository;

import dev.pcshop.entity.Order;
import dev.pcshop.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<Order> findByPublicId(UUID publicId);

    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(Long id);

    List<Order> findTop5ByTelegramChatIdOrderByCreatedAtDesc(long chatId);

    @Query("select o.id from Order o where o.status = dev.pcshop.enums.OrderStatus.AWAITING_PAYMENT and o.createdAt < :before")
    List<Long> findAwaitingCreatedBefore(@Param("before") Instant before);

    Page<Order> findAllByAccountIdOrderByCreatedAtDesc(long accountId, Pageable pageable);

    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Order> findAllByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

    @Query("""
            select count(o) > 0 from Order o join o.items i
            where o.accountId = :accountId and i.productId = :productId and o.paidAt is not null
              and o.status <> dev.pcshop.enums.OrderStatus.CANCELLED""")
    boolean hasBought(@Param("accountId") long accountId, @Param("productId") long productId);
}
