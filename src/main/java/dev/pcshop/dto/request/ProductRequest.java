package dev.pcshop.dto.request;

import dev.pcshop.entity.Product;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashMap;
import java.util.Map;

public record ProductRequest(
        @NotNull(message = "Выберите категорию") Long categoryId,
        @NotBlank(message = "Укажите название") @Size(max = 256, message = "Название длиннее 256 символов") String name,
        @NotBlank(message = "Укажите бренд") @Size(max = 64, message = "Бренд длиннее 64 символов") String brand,
        @NotNull(message = "Укажите цену") @Positive(message = "Цена должна быть больше нуля")
        @Max(value = 10_000_000, message = "Слишком большая цена") Integer price,
        @Positive(message = "Цена до скидки должна быть больше нуля")
        @Max(value = 10_000_000, message = "Слишком большая цена до скидки") Integer oldPrice,
        @Size(max = 5000, message = "Описание длиннее 5000 символов") String description,
        Boolean active,
        Map<String, String> specs,
        @PositiveOrZero(message = "Остаток не может быть отрицательным")
        @Max(value = 100_000, message = "Слишком большой остаток") Integer stock) {

    public ProductRequest {
        specs = specs == null ? Map.of() : specs;
    }

    public static ProductRequest of(Product p) {
        Map<String, String> specs = new LinkedHashMap<>();
        p.getAttributes().forEach((k, v) -> specs.put(k, String.valueOf(v)));
        return new ProductRequest(p.getCategory().getId(), p.getName(), p.getBrand(), p.getPrice(), p.getOldPrice(),
                p.getDescription(), p.isActive(), specs, null);
    }

    public static ProductRequest empty(Long categoryId) {
        return new ProductRequest(categoryId, null, null, null, null, null, true, Map.of(), null);
    }

    public ProductRequest withCategory(Long categoryId) {
        return new ProductRequest(categoryId, name, brand, price, oldPrice, description, active, specs, stock);
    }

    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }

    @AssertTrue(message = "Цена до скидки должна быть больше новой цены")
    public boolean isOldPriceAbovePrice() {
        return oldPrice == null || price == null || oldPrice > price;
    }
}
