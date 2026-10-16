package dev.pcshop.dto.request;

import dev.pcshop.entity.Category;
import dev.pcshop.enums.SpecType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

/**
 * Категория из админки: название, место в меню и характеристики товаров.
 *
 * @param specs строки таблицы характеристик; пустые строки (новые, где ничего не вписали) пропускаются
 */
public record CategoryRequest(
        @NotBlank(message = "Укажите название") @Size(max = 128, message = "Название длиннее 128 символов") String name,
        @NotNull(message = "Укажите место в меню") @PositiveOrZero(message = "Место в меню — число от 0")
        @Max(value = 1000, message = "Место в меню — число до 1000") Integer sortOrder,
        List<@Valid SpecRow> specs) {

    /** Пустых строк для новых характеристик в форме: больше — сохранить и добавить ещё. */
    public static final int NEW_ROWS = 3;

    /**
     * Строка таблицы характеристик.
     *
     * @param key    пусто у новой характеристики: ключ придумается из названия
     * @param remove отмечено «удалить»
     */
    public record SpecRow(String key,
                          @Size(max = 64, message = "Название характеристики длиннее 64 символов") String label,
                          SpecType type, Boolean filter, Boolean remove) {

        public boolean isBlank() {
            return (key == null || key.isBlank()) && (label == null || label.isBlank());
        }

        public boolean isFilter() {
            return Boolean.TRUE.equals(filter);
        }

        public boolean isRemove() {
            return Boolean.TRUE.equals(remove);
        }
    }

    public CategoryRequest {
        specs = specs == null ? List.of() : specs;
    }

    public static CategoryRequest of(Category c) {
        List<SpecRow> rows = new ArrayList<>(c.getSpecs().stream()
                .map(s -> new SpecRow(s.key(), s.label(), s.type(), s.filter(), false)).toList());
        return new CategoryRequest(c.getName(), c.getSortOrder(), rows).withEmptyRows();
    }

    public static CategoryRequest empty(int sortOrder) {
        return new CategoryRequest(null, sortOrder, List.of()).withEmptyRows();
    }

    /** Форма с пустыми строками в конце, куда вписывают новые характеристики. */
    public CategoryRequest withEmptyRows() {
        List<SpecRow> rows = new ArrayList<>(specs.stream().filter(r -> !r.isBlank()).toList());
        for (int i = 0; i < NEW_ROWS; i++) {
            rows.add(new SpecRow(null, null, SpecType.TEXT, false, false));
        }
        return new CategoryRequest(name, sortOrder, rows);
    }
}
