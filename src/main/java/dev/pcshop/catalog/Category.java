package dev.pcshop.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String slug;

    private String name;

    @Column(name = "sort_order")
    private int sortOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<FilterDef> filters = new ArrayList<>();

    protected Category() {
    }

    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public String getName() { return name; }
    public int getSortOrder() { return sortOrder; }
    public List<FilterDef> getFilters() { return filters; }
}
