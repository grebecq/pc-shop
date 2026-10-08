package dev.pcshop.build;

import dev.pcshop.TestcontainersConfig;
import dev.pcshop.account.AccountException;
import dev.pcshop.account.AccountService;
import dev.pcshop.account.RegisterForm;
import dev.pcshop.catalog.CatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.pcshop.build.Slot.CASE;
import static dev.pcshop.build.Slot.COOLER;
import static dev.pcshop.build.Slot.CPU;
import static dev.pcshop.build.Slot.GPU;
import static dev.pcshop.build.Slot.MOTHERBOARD;
import static dev.pcshop.build.Slot.PSU;
import static dev.pcshop.build.Slot.RAM;
import static dev.pcshop.build.Slot.SSD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Правила совместимости на демо-каталоге и сохранённые сборки. */
@SpringBootTest
@Import(TestcontainersConfig.class)
class CompatibilityTest {

    @Autowired CatalogService catalog;
    @Autowired BuildService builds;
    @Autowired AccountService accounts;

    @Test
    void presetsAreCompleteAndCompatible() {
        assertThat(builds.presets()).hasSize(4).allSatisfy(p -> {
            var issues = Compatibility.check(p.build());
            assertThat(issues).as(p.name()).noneMatch(i -> i.isError() || i.isMissing());
            assertThat(Compatibility.complete(issues)).isTrue();
        });
        // Офисная сборка без видеокарты и кулера: графика и кулер есть у процессора
        var office = builds.presets().getFirst().build();
        assertThat(office.has(GPU)).isFalse();
        assertThat(office.has(COOLER)).isFalse();
    }

    @Test
    void socketMemoryAndSizeConflictsAreErrors() {
        var b = build(CPU, "intel-core-i5-14400f", MOTHERBOARD, "msi-pro-b650m-a-wifi",
                RAM, "kingston-fury-beast-16-gb-2x8-ddr4-3200", CASE, "cooler-master-nr200p",
                GPU, "powercolor-hellhound-radeon-rx-9070-xt-16-gb");
        assertThat(errors(b)).anyMatch(t -> t.contains("сокет LGA1700") && t.contains("AM5"))
                .anyMatch(t -> t.contains("DDR5") && t.contains("DDR4"))
                .anyMatch(t -> t.contains("mATX") && t.contains("Mini-ITX"));
        // RX 9070 XT 322 мм в NR200P (до 330 мм) влезает
        assertThat(errors(b)).noneMatch(t -> t.contains("Видеокарта длиной"));
        assertThat(errors(b.with(GPU, product("asus-prime-geforce-rtx-5070-12-gb"))))
                .noneMatch(t -> t.contains("Видеокарта длиной"));
        assertThat(errors(build(GPU, "powercolor-hellhound-radeon-rx-9070-xt-16-gb", CASE, "fractal-design-north")))
                .noneMatch(t -> t.contains("Видеокарта длиной"));
    }

    @Test
    void memoryWithoutBoardIsCheckedAgainstCpu() {
        // LGA1700 понимает обе памяти, AM5 — только DDR5
        assertThat(errors(build(CPU, "intel-core-i5-14400f", RAM, "kingston-fury-beast-16-gb-2x8-ddr4-3200"))).isEmpty();
        assertThat(errors(build(CPU, "amd-ryzen-5-7600", RAM, "kingston-fury-beast-16-gb-2x8-ddr4-3200")))
                .containsExactly("Процессор не поддерживает DDR4.");
    }

    @Test
    void coolerFitsSocketAndCase() {
        assertThat(errors(build(CPU, "intel-core-i5-14400f", COOLER, "noctua-nh-l9a-am5")))
                .anyMatch(t -> t.contains("не ставится на сокет LGA1700"));
        assertThat(errors(build(COOLER, "noctua-nh-d15-g2", CASE, "cooler-master-nr200p")))
                .anyMatch(t -> t.contains("168 мм"));
        assertThat(errors(build(COOLER, "arctic-liquid-freezer-iii-360", CASE, "cooler-master-nr200p")))
                .anyMatch(t -> t.contains("Радиатор 360 мм"));
        assertThat(errors(build(COOLER, "deepcool-le520", CASE, "cooler-master-nr200p"))).isEmpty();

        var weak = Compatibility.check(build(CPU, "amd-ryzen-7-7800x3d", COOLER, "noctua-nh-l9a-am5"));
        assertThat(weak).anyMatch(i -> i.isWarning() && i.text().contains("рассчитан на 65 Вт"));
    }

    @Test
    void powerSupplyIsChecked() {
        var top = build(CPU, "amd-ryzen-9-9900x", GPU, "gigabyte-geforce-rtx-5070-ti-eagle-16-gb");
        assertThat(Compatibility.estimatedDraw(top)).isEqualTo(120 + 300 + Compatibility.BASE_POWER_W);
        assertThat(Compatibility.recommendedPsu(top)).isEqualTo(750);

        assertThat(errors(top.with(PSU, product("deepcool-pk550d-550-vt")))).anyMatch(t -> t.contains("советует блок от 750"));
        assertThat(Compatibility.check(top.with(PSU, product("cougar-gex-650-650-vt"))))
                .anyMatch(i -> i.isWarning() && i.text().contains("впритык"));
        assertThat(Compatibility.check(top.with(PSU, product("corsair-rm850e-850-vt"))))
                .noneMatch(i -> i.slots().contains(PSU));
    }

    @Test
    void missingPartsDependOnCpu() {
        var withF = Compatibility.check(build(CPU, "intel-core-i7-14700kf"));
        assertThat(withF).filteredOn(Issue::isMissing).extracting(Issue::text)
                .anyMatch(t -> t.contains("нет встроенной графики"))
                .anyMatch(t -> t.contains("без кулера"));
        var withBox = Compatibility.check(build(CPU, "amd-ryzen-5-7600"));
        assertThat(withBox).filteredOn(Issue::isMissing).extracting(Issue::text)
                .noneMatch(t -> t.contains("графики") || t.contains("кулера"));
    }

    @Test
    void fastSsdOnOldBoardIsWarning() {
        var b = build(MOTHERBOARD, "msi-pro-b650m-a-wifi", SSD, "crucial-t705-2-tb");
        assertThat(Compatibility.check(b)).anyMatch(i -> i.isWarning() && i.text().contains("PCIe 4.0"));
        assertThat(Compatibility.check(b.with(MOTHERBOARD, product("gigabyte-x870-aorus-elite-wifi7"))))
                .noneMatch(i -> i.text().contains("PCIe"));
    }

    @Test
    void candidatesPutCompatibleFirst() {
        var am5 = build(CPU, "amd-ryzen-5-7600");
        List<Candidate> boards = builds.candidates(am5, MOTHERBOARD);
        assertThat(boards).hasSize(8);
        var fit = boards.stream().filter(Candidate::fits).toList();
        assertThat(fit).hasSize(4).allMatch(c -> c.product().str("socket").equals("AM5"));
        assertThat(boards.subList(0, 4)).containsExactlyElementsOf(fit);
        assertThat(boards.getLast().note()).contains("сокет");
        // Ссылка выбора сохраняет остальную сборку
        assertThat(fit.getFirst().link()).contains("cpu=" + am5.part(CPU).getId()).contains("mb=");
    }

    @Test
    void brokenLinksAreIgnored() {
        long cpu = product("amd-ryzen-5-7600").getId();
        long ram = product("adata-xpg-lancer-16-gb-2x8-ddr5-5600").getId();
        // Процессор в слоте видеокарты, мусор, несуществующий id, лишний параметр
        var b = builds.fromParams(Map.of("cpu", String.valueOf(cpu), "gpu", String.valueOf(cpu),
                "ram", "abc", "ssd", "999999", "x", "1", "psu", "-5"));
        assertThat(b.parts()).containsOnlyKeys(CPU);
        assertThat(builds.fromParams(Map.of("ram", ram)).has(RAM)).isTrue();
    }

    @Test
    void savedBuildsBelongToAccount() {
        long anna = account();
        long boris = account();
        var preset = builds.presets().get(2).build();
        var saved = builds.save(anna, "  Игровой для сына  ", preset);
        assertThat(saved.getName()).isEqualTo("Игровой для сына");

        var list = builds.saved(anna);
        assertThat(list).hasSize(1);
        assertThat(list.getFirst().build().ids()).isEqualTo(preset.ids());
        assertThat(list.getFirst().complete()).isTrue();
        assertThat(builds.saved(boris)).isEmpty();

        assertThatThrownBy(() -> builds.deleteSaved(boris, saved.getId())).isInstanceOf(AccountException.class);
        assertThatThrownBy(() -> builds.save(anna, "", Build.empty())).isInstanceOf(AccountException.class);
        assertThat(builds.save(anna, "", preset).getName()).startsWith("Сборка за ");
        builds.deleteSaved(anna, saved.getId());
        assertThat(builds.saved(anna)).hasSize(1);
    }

    @Test
    void savedBuildsAreLimited() {
        long id = account();
        var b = builds.presets().getFirst().build();
        for (int i = 0; i < BuildService.MAX_SAVED; i++) {
            builds.save(id, "Сборка " + i, b);
        }
        assertThatThrownBy(() -> builds.save(id, "Лишняя", b)).hasMessageContaining("не больше");
    }

    private long account() {
        return accounts.register(new RegisterForm("Тест", "t-" + UUID.randomUUID() + "@example.com", "secret-123"))
                .getAccountId();
    }

    private Build build(Object... slotsAndSlugs) {
        Build b = Build.empty();
        for (int i = 0; i < slotsAndSlugs.length; i += 2) {
            b = b.with((Slot) slotsAndSlugs[i], product((String) slotsAndSlugs[i + 1]));
        }
        return b;
    }

    private dev.pcshop.catalog.Product product(String slug) {
        return catalog.product(slug);
    }

    private static List<String> errors(Build b) {
        return Compatibility.check(b).stream().filter(Issue::isError).map(Issue::text).toList();
    }
}
