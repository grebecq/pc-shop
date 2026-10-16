package dev.pcshop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Категория каталога. Владелец заводит и правит их в админке; slug после создания не меняется — на нём ссылки. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "category")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Характеристики товаров категории в порядке вывода. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<Spec> specs = new ArrayList<>();

    public Category(String slug, String name, int sortOrder, List<Spec> specs) {
        this.slug = slug;
        update(name, sortOrder, specs);
    }

    public void update(String name, int sortOrder, List<Spec> specs) {
        this.name = name;
        this.sortOrder = sortOrder;
        this.specs = new ArrayList<>(specs);
    }

    /** Характеристики, по которым можно фильтровать категорию. */
    public List<FilterDef> getFilters() {
        return specs.stream().filter(Spec::filter).map(s -> new FilterDef(s.key(), s.label())).toList();
    }

    public Optional<Spec> spec(String key) {
        return specs.stream().filter(s -> s.key().equals(key)).findFirst();
    }
}
