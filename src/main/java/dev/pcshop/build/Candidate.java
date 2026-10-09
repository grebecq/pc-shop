package dev.pcshop.build;

import dev.pcshop.catalog.Product;

import java.util.List;

/** Товар в списке выбора для слота и его проблемы с уже выбранными деталями. */
public record Candidate(Product product, List<Issue> problems, String link) {

    public boolean fits() {
        return problems.stream().noneMatch(Issue::isError);
    }

    /** Первая причина, почему не подходит, иначе первое предупреждение, иначе null. */
    public String note() {
        return problems.stream().filter(Issue::isError).findFirst()
                .or(() -> problems.stream().filter(Issue::isWarning).findFirst())
                .map(Issue::text).orElse(null);
    }
}
