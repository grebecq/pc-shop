package dev.pcshop.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<Order> findByPublicId(UUID publicId);

    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(Long id);

    List<Order> findTop5ByTelegramChatIdOrderByCreatedAtDesc(long chatId);

    @org.springframework.data.jpa.repository.Query(
            "select o.id from Order o where o.status = dev.pcshop.order.OrderStatus.AWAITING_PAYMENT and o.createdAt < :before")
    List<Long> findAwaitingCreatedBefore(java.time.Instant before);

    Page<Order> findAllByAccountIdOrderByCreatedAtDesc(long accountId, Pageable pageable);

    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Order> findAllByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);
}
