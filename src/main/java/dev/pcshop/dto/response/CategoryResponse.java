package dev.pcshop.dto.response;

import dev.pcshop.entity.FilterDef;

import java.util.List;

public record CategoryResponse(String slug, String name, List<FilterDef> filters) {
}
