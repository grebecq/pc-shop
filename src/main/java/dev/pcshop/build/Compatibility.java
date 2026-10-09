package dev.pcshop.build;

import dev.pcshop.catalog.Product;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static dev.pcshop.build.Slot.CASE;
import static dev.pcshop.build.Slot.COOLER;
import static dev.pcshop.build.Slot.CPU;
import static dev.pcshop.build.Slot.GPU;
import static dev.pcshop.build.Slot.MOTHERBOARD;
import static dev.pcshop.build.Slot.PSU;
import static dev.pcshop.build.Slot.RAM;
import static dev.pcshop.build.Slot.SSD;

/**
 * Правила совместимости. Каждое правило смотрит только на уже выбранные детали,
 * поэтому сборку можно проверять на любом шаге, а не только целиком.
 */
public final class Compatibility {

    /** Плата, память, накопитель и вентиляторы — примерно столько же сверх процессора и видеокарты. */
    static final int BASE_POWER_W = 75;
    /** Запас по мощности блока питания: под пиковыми нагрузками потребление выше паспортного. */
    static final double PSU_HEADROOM = 1.4;

    /**
     * Насколько можно отступить от блока, который советует производитель видеокарты: совет с запасом,
     * на 50–100 Вт меньше обычно работает, но дальше начинаются отключения под нагрузкой.
     */
    static final int GPU_ADVICE_SLACK_W = 100;

    private static final List<String> BOARD_SIZES = List.of("Mini-ITX", "mATX", "ATX");

    private Compatibility() {
    }

    public static List<Issue> check(Build build) {
        List<Issue> issues = new ArrayList<>();
        var cpu = build.get(CPU);
        var mb = build.get(MOTHERBOARD);
        var ram = build.get(RAM);
        var gpu = build.get(GPU);
        var cooler = build.get(COOLER);
        var psu = build.get(PSU);
        var pcCase = build.get(CASE);
        var ssd = build.get(SSD);

        // Процессор и плата: один сокет
        if (cpu.isPresent() && mb.isPresent() && !cpu.get().str("socket").equals(mb.get().str("socket"))) {
            issues.add(Issue.error("Процессор под сокет " + cpu.get().str("socket") + ", а плата под "
                    + mb.get().str("socket") + ".", CPU, MOTHERBOARD));
        }

        // Память: тип решает плата, а если её ещё нет — процессор
        if (ram.isPresent()) {
            String type = ram.get().str("memory_type");
            if (mb.isPresent()) {
                if (!mb.get().str("memory_type").equals(type)) {
                    issues.add(Issue.error("Плата работает с " + mb.get().str("memory_type") + ", а память " + type + ".",
                            RAM, MOTHERBOARD));
                }
            } else if (cpu.isPresent() && !supports(cpu.get().str("memory_type"), type)) {
                issues.add(Issue.error("Процессор не поддерживает " + type + ".", RAM, CPU));
            }
        }
        // Плата и процессор по памяти: например, плата DDR4 и процессор только с DDR5
        if (cpu.isPresent() && mb.isPresent() && cpu.get().str("socket").equals(mb.get().str("socket"))
                && !supports(cpu.get().str("memory_type"), mb.get().str("memory_type"))) {
            issues.add(Issue.error("Процессор не работает с памятью " + mb.get().str("memory_type") + ", а плата рассчитана на неё.",
                    CPU, MOTHERBOARD));
        }

        // Плата влезает в корпус, если корпус рассчитан на такой же или больший форм-фактор
        if (mb.isPresent() && pcCase.isPresent()) {
            String board = mb.get().str("form_factor");
            String max = pcCase.get().str("form_factor");
            if (BOARD_SIZES.indexOf(board) > BOARD_SIZES.indexOf(max)) {
                issues.add(Issue.error("Плата " + board + " не поместится в корпус, он рассчитан максимум на " + max + ".",
                        MOTHERBOARD, CASE));
            }
        }

        // Длина видеокарты
        if (gpu.isPresent() && pcCase.isPresent() && gpu.get().num("length_mm") > pcCase.get().num("max_gpu_mm")) {
            issues.add(Issue.error("Видеокарта длиной " + gpu.get().num("length_mm") + " мм не влезет: в корпус входит до "
                    + pcCase.get().num("max_gpu_mm") + " мм.", GPU, CASE));
        }

        // Кулер: сокет, габариты, хватит ли его
        if (cooler.isPresent()) {
            Product c = cooler.get();
            Optional<Product> socketSource = cpu.isPresent() ? cpu : mb;
            if (socketSource.isPresent() && !supports(c.str("sockets"), socketSource.get().str("socket"))) {
                issues.add(Issue.error("Кулер не ставится на сокет " + socketSource.get().str("socket") + ".",
                        COOLER, cpu.isPresent() ? CPU : MOTHERBOARD));
            }
            if (pcCase.isPresent()) {
                if (c.num("height_mm") > pcCase.get().num("max_cooler_mm")) {
                    issues.add(Issue.error("Кулер высотой " + c.num("height_mm") + " мм не влезет: в корпус входит до "
                            + pcCase.get().num("max_cooler_mm") + " мм.", COOLER, CASE));
                }
                int maxRadiator = pcCase.get().num("max_radiator_mm");
                if (c.num("radiator_mm") > maxRadiator) {
                    issues.add(Issue.error(maxRadiator == 0
                            ? "В этот корпус СЖО не ставится, нужен башенный или низкопрофильный кулер."
                            : "Радиатор " + c.num("radiator_mm") + " мм не встанет: корпус вмещает до " + maxRadiator + " мм.",
                            COOLER, CASE));
                }
            }
            // Не у всех кулеров производитель указывает рассеиваемую мощность — тогда не проверяем
            if (cpu.isPresent() && c.num("max_tdp") > 0 && c.num("max_tdp") < cpu.get().num("tdp")) {
                issues.add(Issue.warning("Кулер рассчитан на " + c.num("max_tdp") + " Вт, а процессор выделяет "
                        + cpu.get().num("tdp") + " Вт: под нагрузкой он будет сбрасывать частоты.", COOLER, CPU));
            }
        }

        // Блок питания
        if (psu.isPresent()) {
            int power = psu.get().num("power");
            int draw = estimatedDraw(build);
            int gpuMinimum = build.get(GPU).map(p -> p.num("recommended_psu") - GPU_ADVICE_SLACK_W).orElse(0);
            if (power < draw) {
                issues.add(Issue.error("Блоку питания " + power + " Вт не хватит: сборка потребляет около " + draw + " Вт.",
                        PSU, GPU, CPU));
            } else if (power < gpuMinimum) {
                issues.add(Issue.error("Для этой видеокарты производитель советует блок от "
                        + build.get(GPU).get().num("recommended_psu") + " Вт, " + power + " Вт слишком мало.", PSU, GPU));
            } else if (power < recommendedPsu(build)) {
                issues.add(Issue.warning("Мощности впритык: рекомендуем блок от " + recommendedPsu(build) + " Вт.",
                        PSU, GPU, CPU));
            }
        }

        // Быстрый SSD в плате без PCIe 5.0
        if (ssd.isPresent() && mb.isPresent() && ssd.get().str("interface").contains("PCIe 5.0") && !mb.get().flag("m2_gen5")) {
            issues.add(Issue.warning("На этой плате SSD с PCIe 5.0 будет работать на скорости PCIe 4.0.", SSD, MOTHERBOARD));
        }

        // Нет в наличии
        build.parts().forEach((slot, p) -> {
            if (!p.inStock()) {
                issues.add(Issue.warning(p.getName() + " сейчас нет в наличии.", slot));
            }
        });

        // Чего не хватает
        for (Slot slot : Slot.values()) {
            if (slot.required() && !build.has(slot)) {
                issues.add(Issue.missing("Не выбран: " + slot.title().toLowerCase() + ".", slot));
            }
        }
        if (!build.has(GPU) && cpu.isPresent() && !cpu.get().flag("igpu")) {
            issues.add(Issue.missing("У процессора нет встроенной графики, без видеокарты не будет изображения.", GPU));
        }
        if (!build.has(COOLER) && cpu.isPresent() && !cpu.get().flag("box_cooler")) {
            issues.add(Issue.missing("Процессор продаётся без кулера, нужно выбрать охлаждение.", COOLER));
        }
        return issues;
    }

    /**
     * Почему товар не подходит в слот при остальных выбранных деталях. Пусто — подходит.
     * Считаем только ошибки, которые касаются этого слота: несовместимость между другими деталями тут ни при чём.
     */
    public static List<Issue> problems(Build build, Slot slot, Product candidate) {
        return check(build.with(slot, candidate)).stream()
                .filter(i -> !i.isMissing() && i.slots().contains(slot))
                .toList();
    }

    /** Примерное потребление под нагрузкой, Вт. */
    public static int estimatedDraw(Build build) {
        return build.get(CPU).map(p -> p.num("tdp")).orElse(0)
                + build.get(GPU).map(p -> p.num("tdp")).orElse(0)
                + BASE_POWER_W;
    }

    /** Рекомендуемая мощность блока: потребление с запасом, округлённое вверх до 50 Вт, но не ниже совета производителя видеокарты. */
    public static int recommendedPsu(Build build) {
        int withHeadroom = (int) Math.ceil(estimatedDraw(build) * PSU_HEADROOM / 50) * 50;
        int gpuAdvice = build.get(GPU).map(p -> p.num("recommended_psu")).orElse(0);
        return Math.max(withHeadroom, gpuAdvice);
    }

    /** Сборка готова к заказу: ничего не забыто и нет ошибок. */
    public static boolean complete(List<Issue> issues) {
        return issues.stream().noneMatch(i -> i.isError() || i.isMissing());
    }

    /** "DDR4, DDR5" содержит "DDR5". */
    private static boolean supports(String list, String value) {
        return Arrays.stream(list.split(",")).map(String::strip).anyMatch(value::equals);
    }
}
