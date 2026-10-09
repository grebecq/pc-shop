package dev.pcshop.dto.response;

import dev.pcshop.entity.Product;
import dev.pcshop.model.Issue;

import java.util.List;

/** Товар в списке выбора для слота и его проблемы с уже выбранными деталями. */
public record CandidateResponse(Product product, List<Issue> problems, String link) {

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
