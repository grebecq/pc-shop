package dev.pcshop.entity;

import dev.pcshop.dto.request.ReviewRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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

/** Отзыв о товаре. Оставить его может только тот, кто этот товар купил. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "review")
@EntityListeners(AuditingEntityListener.class)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private long productId;

    /** null у демо-отзывов: их никто не покупал на этом сайте. */
    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "author_name", nullable = false)
    private String authorName;

    @Column(nullable = false)
    private int rating;

    private String pros;

    private String cons;

    @Column(nullable = false)
    private String text;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Review(long productId, long accountId, String authorName, ReviewRequest request) {
        this.productId = productId;
        this.accountId = accountId;
        this.authorName = authorName;
        this.rating = request.rating();
        this.pros = request.pros();
        this.cons = request.cons();
        this.text = request.text();
    }

    /** Отзыв от покупателя этого магазина, а не демо-данные. */
    public boolean verified() {
        return accountId != null;
    }
}
