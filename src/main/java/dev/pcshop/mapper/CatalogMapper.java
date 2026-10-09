package dev.pcshop.mapper;

import dev.pcshop.dto.response.CategoryResponse;
import dev.pcshop.dto.response.ProductResponse;
import dev.pcshop.entity.Category;
import dev.pcshop.entity.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

// Новое поле в DTO без маппинга — ошибка компиляции, а не тихий null в ответе
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CatalogMapper {

    CategoryResponse toResponse(Category category);

    List<CategoryResponse> toCategoryResponses(List<Category> categories);

    @Mapping(target = "category", source = "category.slug")
    ProductResponse toResponse(Product product);

    List<ProductResponse> toProductResponses(List<Product> products);
}
