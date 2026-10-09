package dev.pcshop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.Instant;

/** «Сообщить о поступлении»: покупатель ждёт товар, которого нет в наличии. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "stock_alert")
@IdClass(StockAlert.Key.class)
@EntityListeners(AuditingEntityListener.class)
public class StockAlert {

    @Id
    @Column(name = "account_id")
    private long accountId;

    @Id
    @Column(name = "product_id")
    private long productId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public StockAlert(long accountId, long productId) {
        this.accountId = accountId;
        this.productId = productId;
    }

    /** Составной ключ: на один товар покупатель подписан один раз. */
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private long accountId;
        private long productId;
    }
}
