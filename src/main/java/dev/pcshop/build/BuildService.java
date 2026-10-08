package dev.pcshop.build;

import dev.pcshop.account.AccountException;
import dev.pcshop.catalog.CatalogService;
import dev.pcshop.catalog.Product;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static dev.pcshop.build.Slot.CASE;
import static dev.pcshop.build.Slot.COOLER;
import static dev.pcshop.build.Slot.CPU;
import static dev.pcshop.build.Slot.GPU;
import static dev.pcshop.build.Slot.MOTHERBOARD;
import static dev.pcshop.build.Slot.PSU;
import static dev.pcshop.build.Slot.RAM;
import static dev.pcshop.build.Slot.SSD;

/** Конфигуратор: сборка из ссылки, список подходящих деталей, готовые и сохранённые сборки. */
@Service
@Transactional(readOnly = true)
public class BuildService {

    static final int MAX_SAVED = 20;

    /** Готовые сборки задаём по slug товаров: id в разных базах могут отличаться. */
    private static final List<PresetDef> PRESETS = List.of(
            new PresetDef("Для офиса и учёбы", "Документы, браузер, видеозвонки. Графика встроена в процессор.", Map.of(
                    CPU, "amd-ryzen-5-7600", MOTHERBOARD, "msi-pro-b650m-a-wifi",
                    RAM, "adata-xpg-lancer-16-gb-2x8-ddr5-5600", SSD, "kingston-nv3-1-tb",
                    PSU, "deepcool-pk550d-550-vt", CASE, "zalman-p30")),
            new PresetDef("Игровой Full HD", "Современные игры в 1080p на высоких настройках.", Map.of(
                    CPU, "intel-core-i5-14400f", MOTHERBOARD, "asus-prime-b760m-a-d4", COOLER, "id-cooling-se-224-xts",
                    RAM, "patriot-viper-steel-32-gb-2x16-ddr4-3600", GPU, "palit-geforce-rtx-5060-dual-8-gb",
                    SSD, "kingston-nv3-1-tb", PSU, "cougar-gex-650-650-vt", CASE, "deepcool-cc560")),
            new PresetDef("Игровой 2K", "Лучший игровой процессор и RTX 5070 для 1440p.", Map.of(
                    CPU, "amd-ryzen-7-7800x3d", MOTHERBOARD, "asus-tuf-gaming-b650-plus-wifi",
                    COOLER, "thermalright-peerless-assassin-120-se", RAM, "kingston-fury-beast-32-gb-2x16-ddr5-6000",
                    GPU, "asus-prime-geforce-rtx-5070-12-gb", SSD, "samsung-990-pro-1-tb",
                    PSU, "be-quiet-pure-power-12-m-750-vt", CASE, "lian-li-lancool-216")),
            new PresetDef("Монтаж и 4K", "12 ядер, 64 ГБ памяти и быстрый SSD для работы с видео.", Map.of(
                    CPU, "amd-ryzen-9-9900x", MOTHERBOARD, "gigabyte-x870-aorus-elite-wifi7",
                    COOLER, "arctic-liquid-freezer-iii-360", RAM, "corsair-vengeance-64-gb-2x32-ddr5-6000",
                    GPU, "gigabyte-geforce-rtx-5070-ti-eagle-16-gb", SSD, "crucial-t705-2-tb",
                    PSU, "corsair-rm850e-850-vt", CASE, "fractal-design-north")));

    private final CatalogService catalog;
    private final SavedBuildRepository saved;
    private final JdbcClient jdbc;

    BuildService(CatalogService catalog, SavedBuildRepository saved, JdbcClient jdbc) {
        this.catalog = catalog;
        this.saved = saved;
        this.jdbc = jdbc;
    }

    /**
     * Сборка из параметров ссылки (cpu=1&mb=5). Незнакомые слоты, кривые id, снятые с продажи товары
     * и товар не той категории молча пропускаются: ссылка могла устареть.
     */
    public Build fromParams(Map<String, ?> params) {
        Map<Slot, Long> ids = new EnumMap<>(Slot.class);
        params.forEach((key, value) -> Slot.byKey(key).ifPresent(slot -> {
            Long id = parseId(value);
            if (id != null) {
                ids.put(slot, id);
            }
        }));
        if (ids.isEmpty()) {
            return Build.empty();
        }
        Map<Long, Product> products = catalog.productsByIds(ids.values()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Slot, Product> parts = new EnumMap<>(Slot.class);
        ids.forEach((slot, id) -> {
            Product p = products.get(id);
            if (p != null && p.getCategory().getSlug().equals(slot.category())) {
                parts.put(slot, p);
            }
        });
        return new Build(parts);
    }

    /** Товары для слота: сначала подходящие и в наличии, потом остальные; внутри — по цене. */
    public List<Candidate> candidates(Build build, Slot slot) {
        return catalog.productsIn(slot.category()).stream()
                .map(p -> new Candidate(p, Compatibility.problems(build, slot, p), build.linkWith(slot, p)))
                .sorted(Comparator.comparing((Candidate c) -> !c.fits())
                        .thenComparing(c -> !c.product().inStock())
                        .thenComparingInt(c -> c.product().getPrice()))
                .toList();
    }

    public List<Preset> presets() {
        var slugs = PRESETS.stream().flatMap(d -> d.parts().values().stream()).collect(Collectors.toSet());
        Map<String, Product> bySlug = catalog.productsBySlugs(slugs).stream()
                .collect(Collectors.toMap(Product::getSlug, Function.identity()));
        return PRESETS.stream().map(d -> {
            Map<Slot, Product> parts = new EnumMap<>(Slot.class);
            // Товар из готовой сборки могли снять с продажи — тогда слот просто пустой
            d.parts().forEach((slot, slug) -> {
                if (bySlug.containsKey(slug)) {
                    parts.put(slot, bySlug.get(slug));
                }
            });
            return new Preset(d.name(), d.purpose(), new Build(parts));
        }).toList();
    }

    // ---------- Сохранённые сборки в кабинете ----------

    @Transactional
    public SavedBuild save(long accountId, String name, Build build) {
        if (build.isEmpty()) {
            throw new AccountException("Сборка пустая, сохранять нечего");
        }
        if (saved.countByAccountId(accountId) >= MAX_SAVED) {
            throw new AccountException("Можно сохранить не больше " + MAX_SAVED + " сборок. Удалите ненужные в кабинете.");
        }
        String clean = name == null ? "" : name.strip();
        if (clean.isEmpty()) {
            clean = "Сборка за " + dev.pcshop.config.Formats.price(build.total());
        }
        if (clean.length() > 80) {
            clean = clean.substring(0, 80);
        }
        return saved.save(new SavedBuild(accountId, clean, build.ids()));
    }

    public List<SavedView> saved(long accountId) {
        return saved.findAllByAccountIdOrderByCreatedAtDesc(accountId).stream()
                .map(s -> new SavedView(s, fromParams(s.getItems())))
                .toList();
    }

    @Transactional
    public void deleteSaved(long accountId, long id) {
        saved.delete(saved.findByIdAndAccountId(id, accountId)
                .orElseThrow(() -> new AccountException("Сборка не найдена")));
    }

    /** Сохранённая сборка с актуальными товарами и проверкой. */
    public record SavedView(SavedBuild saved, Build build) {
        public List<Issue> issues() {
            return Compatibility.check(build);
        }

        public boolean complete() {
            return Compatibility.complete(issues());
        }
    }

    // ---------- Текущая сборка в Telegram-боте ----------

    public Build botBuild(long chatId) {
        Map<String, String> items = new LinkedHashMap<>();
        jdbc.sql("SELECT e.key, e.value FROM bot_build b, jsonb_each_text(b.items) e WHERE b.chat_id = :chat")
                .param("chat", chatId)
                .query((rs, n) -> items.put(rs.getString(1), rs.getString(2)))
                .list();
        return fromParams(items);
    }

    @Transactional
    public void saveBotBuild(long chatId, Build build) {
        // Ключи — наши слоты, значения — числа, поэтому JSON собираем без библиотеки
        String json = build.ids().entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":" + e.getValue())
                .collect(Collectors.joining(",", "{", "}"));
        jdbc.sql("""
                        INSERT INTO bot_build (chat_id, items) VALUES (:chat, CAST(:items AS jsonb))
                        ON CONFLICT (chat_id) DO UPDATE SET items = EXCLUDED.items""")
                .param("chat", chatId).param("items", json)
                .update();
    }

    private static Long parseId(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            long id = Long.parseLong(String.valueOf(value).strip());
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record PresetDef(String name, String purpose, Map<Slot, String> parts) {
    }
}
