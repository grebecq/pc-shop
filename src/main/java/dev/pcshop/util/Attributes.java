package dev.pcshop.util;

import dev.pcshop.entity.Product;
import dev.pcshop.entity.Spec;
import dev.pcshop.enums.SpecType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

// Характеристики товара
public final class Attributes {

    private static final Map<String, String> SHORT = Map.ofEntries(
            Map.entry("cores", "%s ядер"),
            Map.entry("threads", "%s потоков"),
            Map.entry("tdp", "%s Вт"),
            Map.entry("capacity_gb", "%s ГБ"),
            Map.entry("modules", "%s ГБ"),
            Map.entry("frequency", "%s МГц"),
            Map.entry("memory_gb", "%s ГБ"),
            Map.entry("length_mm", "%s мм"),
            Map.entry("recommended_psu", "БП от %s Вт"),
            Map.entry("read_mbps", "до %s МБ/с"),
            Map.entry("power", "%s Вт"),
            Map.entry("max_gpu_mm", "GPU до %s мм"),
            Map.entry("max_tdp", "до %s Вт"),
            Map.entry("max_cooler_mm", "кулер до %s мм"),
            Map.entry("max_radiator_mm", "СЖО до %s мм"),
            Map.entry("height_mm", "%s мм"),
            Map.entry("radiator_mm", "радиатор %s мм"),
            Map.entry("boost_ghz", "до %s ГГц"),
            Map.entry("boost_mhz", "до %s МГц"),
            Map.entry("cl", "CL%s"));

    private Attributes() {
    }

    public static String value(Object value) {
        if (value instanceof Boolean b) {
            return b ? "да" : "нет";
        }
        return String.valueOf(value);
    }

    public static Map<String, String> readable(Product product) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Spec spec : product.getCategory().getSpecs()) {
            Object value = product.getAttributes().get(spec.key());
            if (value != null) {
                result.put(spec.label(), value(value));
            }
        }
        return result;
    }

    public static String brief(Product product) {
        return product.getCategory().getSpecs().stream()
                .filter(s -> s.type() != SpecType.BOOL && product.getAttributes().get(s.key()) != null)
                .limit(3)
                .map(s -> brief(s, value(product.getAttributes().get(s.key()))))
                .collect(Collectors.joining(" · "));
    }

    private static String brief(Spec spec, String value) {
        if (SHORT.containsKey(spec.key())) {
            return SHORT.get(spec.key()).formatted(value);
        }
        int comma = spec.label().lastIndexOf(", ");
        return spec.type() == SpecType.NUMBER && comma > 0 ? value + " " + spec.label().substring(comma + 2) : value;
    }
}
