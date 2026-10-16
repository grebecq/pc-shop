package dev.pcshop.entity;

import dev.pcshop.enums.StockReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "stock_movement")
@EntityListeners(AuditingEntityListener.class)
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private StockReason reason;

    @Column(nullable = false, updatable = false)
    private int delta;

    @Column(name = "stock_after", nullable = false, updatable = false)
    private int stockAfter;

    @Column(name = "order_id", updatable = false)
    private Long orderId;

    @Column(updatable = false)
    private String comment;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public StockMovement(long productId, StockReason reason, int delta, int stockAfter, Long orderId, String comment) {
        this.productId = productId;
        this.reason = reason;
        this.delta = delta;
        this.stockAfter = stockAfter;
        this.orderId = orderId;
        this.comment = comment;
    }

    public String orderNumber() {
        return orderId == null ? null : Order.number(orderId);
    }
}
