package dev.pcshop.dto.response;

import dev.pcshop.entity.SavedBuild;
import dev.pcshop.model.Build;
import dev.pcshop.model.Issue;

import java.util.List;

/** Сохранённая сборка с актуальными товарами и результатом проверки совместимости. */
public record SavedBuildResponse(SavedBuild saved, Build build, List<Issue> issues, boolean complete) {
}
