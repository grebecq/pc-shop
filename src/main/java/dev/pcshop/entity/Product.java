package dev.pcshop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "product")
@EntityListeners(AuditingEntityListener.class)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String brand;

    /** Цена в рублях. */
    @Column(nullable = false)
    private int price;

    /** Цена до скидки или null, если скидки нет. Всегда больше price (проверяет и база). */
    @Column(name = "old_price")
    private Integer oldPrice;

    @Column(nullable = false)
    private int stock;

    @Column(nullable = false)
    private String description;

    /** Характеристики: у разных категорий разные, поэтому JSONB. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> attributes = new LinkedHashMap<>();

    @Setter
    @Column(nullable = false)
    private boolean active = true;

    @Setter
    @Column(name = "image_url")
    private String imageUrl;

    /** Средняя оценка из отзывов или null. Пересчитывает ReviewRepository.refreshRating, JPA её не пишет. */
    @Column(insertable = false, updatable = false)
    private BigDecimal rating;

    @Column(name = "reviews_count", insertable = false, updatable = false)
    private int reviewsCount;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Числовая характеристика или 0, если её нет. */
    public int num(String key) {
        return attributes.get(key) instanceof Number n ? n.intValue() : 0;
    }

    /** Строковая характеристика или пустая строка. */
    public String str(String key) {
        Object v = attributes.get(key);
        return v == null ? "" : v.toString();
    }

    public boolean flag(String key) {
        return Boolean.TRUE.equals(attributes.get(key));
    }

    public boolean inStock() {
        return stock > 0;
    }

    public boolean onSale() {
        return oldPrice != null;
    }

    /** Скидка в процентах для значка «−12%», округлённая до целого. */
    public int discountPercent() {
        return oldPrice == null ? 0 : Math.round(100f * (oldPrice - price) / oldPrice);
    }

    /** Новая цена и цена до скидки меняются вместе: иначе по дороге старая цена могла бы оказаться меньше новой. */
    public void reprice(int price, Integer oldPrice) {
        if (price <= 0) {
            throw new IllegalArgumentException("Цена должна быть больше нуля");
        }
        if (oldPrice != null && oldPrice <= price) {
            throw new IllegalArgumentException("Цена до скидки должна быть больше новой цены");
        }
        this.price = price;
        this.oldPrice = oldPrice;
    }

    public void setStock(int stock) {
        if (stock < 0) {
            throw new IllegalArgumentException("Остаток не может быть отрицательным");
        }
        this.stock = stock;
    }
}
