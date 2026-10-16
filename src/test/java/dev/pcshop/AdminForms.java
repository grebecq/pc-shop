package dev.pcshop;

import dev.pcshop.entity.Product;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Поля формы товара в админке, заполненные как у товара: тест меняет только нужные. */
public final class AdminForms {

    private AdminForms() {
    }

    /**
     * @param overrides пары «поле, значение»; значение null убирает поле, как снятая галочка
     */
    public static String[] product(Product p, String... overrides) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("categoryId", String.valueOf(p.getCategory().getId()));
        form.put("name", p.getName());
        form.put("brand", p.getBrand());
        form.put("price", String.valueOf(p.getPrice()));
        form.put("oldPrice", p.getOldPrice() == null ? "" : String.valueOf(p.getOldPrice()));
        form.put("description", p.getDescription());
        if (p.isActive()) {
            form.put("active", "true");
        }
        p.getAttributes().forEach((k, v) -> form.put("specs[" + k + "]", String.valueOf(v)));
        for (int i = 0; i < overrides.length; i += 2) {
            if (overrides[i + 1] == null) {
                form.remove(overrides[i]);
            } else {
                form.put(overrides[i], overrides[i + 1]);
            }
        }
        List<String> pairs = new ArrayList<>();
        form.forEach((k, v) -> {
            pairs.add(k);
            pairs.add(v);
        });
        return pairs.toArray(String[]::new);
    }
}
