package dev.pcshop.mapper;

import dev.pcshop.dto.response.CategoryResponse;
import dev.pcshop.dto.response.ProductResponse;
import dev.pcshop.entity.Category;
import dev.pcshop.entity.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CatalogMapper {

    CategoryResponse toResponse(Category category);

    List<CategoryResponse> toCategoryResponses(List<Category> categories);

    @Mapping(target = "category", source = "category.slug")
    ProductResponse toResponse(Product product);

    List<ProductResponse> toProductResponses(List<Product> products);
}
