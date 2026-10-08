package dev.pcshop.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    private String slug;
    private String name;
    private String brand;
    /** Цена в рублях. */
    private int price;
    private int stock;
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> attributes = new LinkedHashMap<>();

    private boolean active = true;
    private Instant createdAt = Instant.now();

    protected Product() {
    }

    public Long getId() { return id; }
    public Category getCategory() { return category; }
    public String getSlug() { return slug; }
    public String getName() { return name; }
    public String getBrand() { return brand; }
    public int getPrice() { return price; }
    public int getStock() { return stock; }
    public String getDescription() { return description; }
    public Map<String, Object> getAttributes() { return attributes; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean inStock() {
        return stock > 0;
    }

    public void setPrice(int price) {
        if (price <= 0) {
            throw new IllegalArgumentException("Цена должна быть больше нуля");
        }
        this.price = price;
    }

    public void setStock(int stock) {
        if (stock < 0) {
            throw new IllegalArgumentException("Остаток не может быть отрицательным");
        }
        this.stock = stock;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
