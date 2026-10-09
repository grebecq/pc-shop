package dev.pcshop.model;

import dev.pcshop.entity.Category;
import dev.pcshop.entity.Product;
import dev.pcshop.util.Attributes;
import dev.pcshop.util.Formats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Таблица сравнения товаров одной категории: столбцы — товары, строки — цена, оценка и характеристики.
 * Сравнивать видеокарту с процессором бессмысленно, поэтому товары разных категорий попадают в разные таблицы.
 */
public record Comparison(Category category, List<Product> products, List<Row> rows) {

    /** Строка таблицы. differs — значения у товаров разные: такие строки подсвечиваются. */
    public record Row(String label, List<String> values, boolean differs) {

        static Row of(String label, List<String> values) {
            return new Row(label, values, values.stream().distinct().count() > 1);
        }
    }

    public static List<Comparison> of(List<Product> products) {
        Map<Long, List<Product>> byCategory = new LinkedHashMap<>();
        products.forEach(p -> byCategory.computeIfAbsent(p.getCategory().getId(), id -> new ArrayList<>()).add(p));
        return byCategory.values().stream()
                .sorted(Comparator.comparingInt(group -> group.getFirst().getCategory().getSortOrder()))
                .map(Comparison::table)
                .toList();
    }

    private static Comparison table(List<Product> group) {
        List<Row> rows = new ArrayList<>();
        rows.add(Row.of("Цена", group.stream().map(p -> Formats.price(p.getPrice())).toList()));
        rows.add(Row.of("Оценка", group.stream()
                .map(p -> p.getRating() == null ? "нет отзывов" : Formats.ratingText(p.getRating()) + " из 5").toList()));
        // Все характеристики, которые есть хотя бы у одного товара: если у другого её нет, ставим прочерк
        var keys = Attributes.sorted(group.stream().flatMap(p -> p.getAttributes().keySet().stream()).toList());
        keys.forEach(key -> rows.add(Row.of(Attributes.label(key), group.stream()
                .map(p -> p.getAttributes().containsKey(key) ? Attributes.value(p.getAttributes().get(key)) : "—")
                .toList())));
        return new Comparison(group.getFirst().getCategory(), group, rows);
    }
}
