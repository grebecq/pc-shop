package dev.pcshop.build;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Сборка, сохранённая в личном кабинете. Хранит только id товаров: цены и совместимость всегда актуальные. */
@Entity
public class SavedBuild {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long accountId;
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> items = new LinkedHashMap<>();

    private Instant createdAt = Instant.now();

    protected SavedBuild() {
    }

    SavedBuild(Long accountId, String name, Map<String, Long> items) {
        this.accountId = accountId;
        this.name = name;
        this.items = new LinkedHashMap<>(items);
    }

    public Long getId() { return id; }
    public Long getAccountId() { return accountId; }
    public String getName() { return name; }
    public Map<String, Object> getItems() { return items; }
    public Instant getCreatedAt() { return createdAt; }
}
