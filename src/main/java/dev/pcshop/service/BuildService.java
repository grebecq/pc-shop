package dev.pcshop.service;

import dev.pcshop.dto.response.CandidateResponse;
import dev.pcshop.dto.response.PresetResponse;
import dev.pcshop.dto.response.SavedBuildResponse;
import dev.pcshop.entity.Product;
import dev.pcshop.entity.SavedBuild;
import dev.pcshop.enums.Slot;
import dev.pcshop.exception.BadRequestException;
import dev.pcshop.exception.ResourceNotFoundException;
import dev.pcshop.model.Build;
import dev.pcshop.model.Issue;
import dev.pcshop.repository.BotBuildRepository;
import dev.pcshop.repository.SavedBuildRepository;
import dev.pcshop.util.Formats;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static dev.pcshop.enums.Slot.CASE;
import static dev.pcshop.enums.Slot.COOLER;
import static dev.pcshop.enums.Slot.CPU;
import static dev.pcshop.enums.Slot.GPU;
import static dev.pcshop.enums.Slot.MOTHERBOARD;
import static dev.pcshop.enums.Slot.PSU;
import static dev.pcshop.enums.Slot.RAM;
import static dev.pcshop.enums.Slot.SSD;

/** Конфигуратор: сборка из ссылки, список подходящих деталей, готовые и сохранённые сборки. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BuildService {

    public static final int MAX_SAVED = 20;

    /** Готовые сборки задаём по slug товаров: id в разных базах могут отличаться. */
    private static final List<PresetDef> PRESETS = List.of(
            new PresetDef("Для офиса и учёбы", "Документы, браузер, видеозвонки. Графика встроена в процессор.", Map.of(
                    CPU, "amd-ryzen-5-7600-oem", MOTHERBOARD, "asus-prime-a620m-k", COOLER, "thermalright-peerless-assassin-120-se",
                    RAM, "kingston-fury-beast-16-gb-2x8-ddr5-5600-cl40", SSD, "kingston-nv3-1-tb-m-2-nvme",
                    PSU, "deepcool-pn650m-650-vt", CASE, "deepcool-cc560-v2-black")),
            new PresetDef("Игровой Full HD", "Современные игры в 1080p на высоких настройках.", Map.of(
                    CPU, "intel-core-i5-14400f-oem", MOTHERBOARD, "gigabyte-b760m-ds3h-ddr4", COOLER, "thermalright-peerless-assassin-120-se",
                    RAM, "g-skill-trident-z-rgb-32-gb-2x16-ddr4-3600-cl18", GPU, "asus-dual-geforce-rtx-5060-oc-8-gb",
                    SSD, "kingston-nv3-1-tb-m-2-nvme", PSU, "deepcool-pn650m-650-vt", CASE, "deepcool-cc560-v2-black")),
            new PresetDef("Игровой 2K", "Лучший по цене игровой процессор и RTX 5070 для 1440p.", Map.of(
                    CPU, "amd-ryzen-7-7800x3d-oem", MOTHERBOARD, "msi-pro-b650-p-wifi",
                    COOLER, "thermalright-peerless-assassin-120-se", RAM, "kingston-fury-beast-32-gb-2x16-ddr5-6000-cl30",
                    GPU, "palit-geforce-rtx-5070-infinity-3-12-gb", SSD, "samsung-990-pro-1-tb-m-2-nvme",
                    PSU, "deepcool-pn750d-750-vt", CASE, "lian-li-lancool-216x-black")),
            new PresetDef("Монтаж и 4K", "12 ядер, 64 ГБ памяти и быстрый SSD для работы с видео.", Map.of(
                    CPU, "amd-ryzen-9-9900x-oem", MOTHERBOARD, "gigabyte-x870-aorus-elite-wifi7",
                    COOLER, "arctic-freezer-36-a-rgb-white", RAM, "g-skill-ripjaws-s5-64-gb-2x32-ddr5-6000-cl30",
                    GPU, "msi-geforce-rtx-5070-ti-ventus-3x-oc-16-gb", SSD, "samsung-9100-pro-2-tb-m-2-nvme",
                    PSU, "deepcool-pn850m-850-vt", CASE, "lian-li-lancool-216x-black")));

    private final CompatibilityService compatibility;
    private final CatalogService catalog;
    private final SavedBuildRepository saved;
    private final BotBuildRepository botBuilds;

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
    public List<CandidateResponse> candidates(Build build, Slot slot) {
        return catalog.productsIn(slot.category()).stream()
                .map(p -> new CandidateResponse(p, compatibility.problems(build, slot, p), build.linkWith(slot, p)))
                .sorted(Comparator.comparing((CandidateResponse c) -> !c.fits())
                        .thenComparing(c -> !c.product().inStock())
                        .thenComparingInt(c -> c.product().getPrice()))
                .toList();
    }

    public List<PresetResponse> presets() {
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
            return new PresetResponse(d.name(), d.purpose(), new Build(parts));
        }).toList();
    }

    // ---------- Сохранённые сборки в кабинете ----------

    @Transactional
    public SavedBuild save(long accountId, String name, Build build) {
        if (build.isEmpty()) {
            throw new BadRequestException("Сборка пустая, сохранять нечего");
        }
        if (saved.countByAccountId(accountId) >= MAX_SAVED) {
            throw new BadRequestException("Можно сохранить не больше " + MAX_SAVED + " сборок. Удалите ненужные в кабинете.");
        }
        String clean = name == null ? "" : name.strip();
        if (clean.isEmpty()) {
            clean = "Сборка за " + Formats.price(build.total());
        }
        if (clean.length() > 80) {
            clean = clean.substring(0, 80);
        }
        return saved.save(new SavedBuild(accountId, clean, build.ids()));
    }

    public List<SavedBuildResponse> saved(long accountId) {
        return saved.findAllByAccountIdOrderByCreatedAtDesc(accountId).stream()
                .map(s -> {
                    Build build = fromParams(s.getItems());
                    List<Issue> issues = compatibility.check(build);
                    return new SavedBuildResponse(s, build, issues, compatibility.complete(issues));
                })
                .toList();
    }

    @Transactional
    public void deleteSaved(long accountId, long id) {
        saved.delete(saved.findByIdAndAccountId(id, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Сборка не найдена")));
    }

    // ---------- Текущая сборка в Telegram-боте ----------

    public Build botBuild(long chatId) {
        return fromParams(botBuilds.find(chatId));
    }

    @Transactional
    public void saveBotBuild(long chatId, Build build) {
        botBuilds.save(chatId, build.ids());
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
