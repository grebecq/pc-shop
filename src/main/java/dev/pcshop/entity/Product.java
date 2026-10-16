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

    @Column(nullable = false)
    private int price;

    @Column(name = "old_price")
    private Integer oldPrice;

    @Column(nullable = false, insertable = false, updatable = false)
    private int stock;

    @Column(nullable = false)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> attributes = new LinkedHashMap<>();

    @Setter
    @Column(nullable = false)
    private boolean active = true;

    @Setter
    @Column(name = "image_url")
    private String imageUrl;

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

    public Product(Category category, String slug) {
        this.category = category;
        this.slug = slug;
    }

    public void edit(Category category, String name, String brand, String description, Map<String, Object> attributes) {
        this.category = category;
        this.name = name;
        this.brand = brand;
        this.description = description;
        this.attributes = new LinkedHashMap<>(attributes);
    }

    public int num(String key) {
        return attributes.get(key) instanceof Number n ? n.intValue() : 0;
    }

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

    public int discountPercent() {
        return oldPrice == null ? 0 : Math.round(100f * (oldPrice - price) / oldPrice);
    }

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
}
