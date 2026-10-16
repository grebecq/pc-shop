package dev.pcshop.service;

import dev.pcshop.TestcontainersConfig;
import dev.pcshop.dto.request.RegisterRequest;
import dev.pcshop.dto.response.CandidateResponse;
import dev.pcshop.entity.Product;
import dev.pcshop.enums.Slot;
import dev.pcshop.exception.BadRequestException;
import dev.pcshop.exception.ResourceNotFoundException;
import dev.pcshop.model.Build;
import dev.pcshop.model.Issue;
import dev.pcshop.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.pcshop.enums.Slot.CASE;
import static dev.pcshop.enums.Slot.COOLER;
import static dev.pcshop.enums.Slot.CPU;
import static dev.pcshop.enums.Slot.GPU;
import static dev.pcshop.enums.Slot.MOTHERBOARD;
import static dev.pcshop.enums.Slot.PSU;
import static dev.pcshop.enums.Slot.RAM;
import static dev.pcshop.enums.Slot.SSD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfig.class)
class CompatibilityServiceTest {

    @Autowired CompatibilityService compatibility;
    @Autowired BuildService builds;
    @Autowired AccountService accounts;
    @Autowired JdbcClient jdbc;
    @Autowired ProductRepository products;

    @Test
    void presetsAreCompleteAndCompatible() {
        assertThat(builds.presets()).hasSize(4).allSatisfy(p -> {
            var issues = compatibility.check(p.build());
            assertThat(issues).as(p.name()).noneMatch(i -> i.isError() || i.isMissing());
            assertThat(compatibility.complete(issues)).isTrue();
        });

        assertThat(builds.presets().getFirst().build().has(GPU)).isFalse();
    }

    @Test
    void socketMemoryAndSizeConflictsAreErrors() {
        var b = build(CPU, "intel-core-i5-14400f-oem", MOTHERBOARD, "asus-prime-a620m-k",
                RAM, "g-skill-ripjaws-v-16-gb-2x8-ddr4-3200-cl16", CASE, "deepcool-ch160-black",
                GPU, "asus-prime-radeon-rx-9070-oc-16-gb");
        assertThat(errors(b)).anyMatch(t -> t.contains("сокет LGA1700") && t.contains("AM5"))
                .anyMatch(t -> t.contains("DDR5") && t.contains("DDR4"))
                .anyMatch(t -> t.contains("mATX") && t.contains("Mini-ITX"));

        assertThat(errors(b)).noneMatch(t -> t.contains("Видеокарта длиной"));
        assertThat(errors(b.with(GPU, product("sapphire-nitro-plus-radeon-rx-9070-xt-16-gb"))))
                .anyMatch(t -> t.contains("Видеокарта длиной 331 мм"));
        assertThat(errors(build(GPU, "sapphire-nitro-plus-radeon-rx-9070-xt-16-gb", CASE, "lian-li-lancool-217-black")))
                .noneMatch(t -> t.contains("Видеокарта длиной"));
    }

    @Test
    void memoryWithoutBoardIsCheckedAgainstCpu() {

        assertThat(errors(build(CPU, "intel-core-i5-14400f-oem", RAM, "g-skill-ripjaws-v-16-gb-2x8-ddr4-3200-cl16"))).isEmpty();
        assertThat(errors(build(CPU, "amd-ryzen-5-7600-oem", RAM, "g-skill-ripjaws-v-16-gb-2x8-ddr4-3200-cl16")))
                .containsExactly("Процессор не поддерживает DDR4.");
    }

    @Test
    void coolerFitsSocketAndCase() {

        var amdOnly = product("deepcool-ak620");
        amdOnly.getAttributes().put("sockets", "AM4, AM5");
        amdOnly.getAttributes().put("height_mm", 180);
        assertThat(errors(build(CPU, "intel-core-i5-14400f-oem").with(COOLER, amdOnly)))
                .anyMatch(t -> t.contains("не ставится на сокет LGA1700"));
        assertThat(errors(build(CASE, "deepcool-cc560-v2-black").with(COOLER, amdOnly)))
                .anyMatch(t -> t.contains("Кулер высотой 180 мм"));

        assertThat(errors(build(COOLER, "deepcool-lm360", CASE, "zalman-p10-namu-white")))
                .anyMatch(t -> t.contains("Радиатор 360 мм"));
        assertThat(errors(build(COOLER, "deepcool-lm360", CASE, "deepcool-ch160-black")))
                .anyMatch(t -> t.contains("СЖО не ставится"));
        assertThat(errors(build(COOLER, "deepcool-le240-v2", CASE, "zalman-p10-namu-white"))).isEmpty();

        var weak = compatibility.check(build(CPU, "amd-ryzen-9-9950x-oem", COOLER, "id-cooling-is-55-black"));
        assertThat(weak).anyMatch(i -> i.isWarning() && i.text().contains("рассчитан на 125 Вт"));

        assertThat(compatibility.check(build(CPU, "amd-ryzen-9-9950x-oem", COOLER, "arctic-liquid-freezer-iii-pro-360-a-rgb-white")))
                .noneMatch(i -> i.slots().contains(COOLER));
    }

    @Test
    void powerSupplyIsChecked() {
        var top = build(CPU, "amd-ryzen-9-9900x-oem", GPU, "msi-geforce-rtx-5070-ti-ventus-3x-oc-16-gb");
        assertThat(compatibility.estimatedDraw(top)).isEqualTo(120 + 300 + CompatibilityService.BASE_POWER_W);
        assertThat(compatibility.recommendedPsu(top)).isEqualTo(750);

        assertThat(errors(top.with(PSU, product("deepcool-pf550x-550-vt")))).anyMatch(t -> t.contains("советует блок от 750"));
        assertThat(compatibility.check(top.with(PSU, product("deepcool-pn650m-650-vt"))))
                .anyMatch(i -> i.isWarning() && i.text().contains("впритык"));
        assertThat(compatibility.check(top.with(PSU, product("deepcool-pn850m-850-vt"))))
                .noneMatch(i -> i.slots().contains(PSU));
    }

    @Test
    void missingPartsDependOnCpu() {
        var withF = compatibility.check(build(CPU, "intel-core-i7-14700kf-oem"));
        assertThat(withF).filteredOn(Issue::isMissing).extracting(Issue::text)
                .anyMatch(t -> t.contains("нет встроенной графики"))
                .anyMatch(t -> t.contains("без кулера"));
        var withGraphics = compatibility.check(build(CPU, "amd-ryzen-5-7600-oem"));
        assertThat(withGraphics).filteredOn(Issue::isMissing).extracting(Issue::text)
                .noneMatch(t -> t.contains("графики"))
                .anyMatch(t -> t.contains("без кулера"));
    }

    @Test
    void fastSsdOnOldBoardIsWarning() {
        var b = build(MOTHERBOARD, "msi-pro-b650-p-wifi", SSD, "samsung-9100-pro-2-tb-m-2-nvme");
        assertThat(compatibility.check(b)).anyMatch(i -> i.isWarning() && i.text().contains("PCIe 4.0"));
        assertThat(compatibility.check(b.with(MOTHERBOARD, product("gigabyte-x870-aorus-elite-wifi7"))))
                .noneMatch(i -> i.text().contains("PCIe"));
    }

    @Test
    void candidatesPutCompatibleFirst() {
        var am5 = build(CPU, "amd-ryzen-5-7600-oem");
        List<CandidateResponse> boards = builds.candidates(am5, MOTHERBOARD);
        assertThat(boards).hasSize(24);
        var fit = boards.stream().filter(CandidateResponse::fits).toList();
        assertThat(fit).hasSize(12).allMatch(c -> c.product().str("socket").equals("AM5"));
        assertThat(boards.subList(0, 12)).containsExactlyElementsOf(fit);
        assertThat(boards.getLast().note()).contains("сокет");

        assertThat(fit.getFirst().link()).contains("cpu=" + am5.part(CPU).getId()).contains("mb=");
    }

    @Test
    void brokenLinksAreIgnored() {
        long cpu = product("amd-ryzen-5-7600-oem").getId();
        long ram = product("kingston-fury-beast-16-gb-2x8-ddr5-5600-cl40").getId();
        long retired = jdbc.sql("SELECT id FROM product WHERE slug = 'amd-ryzen-5-7600'").query(Long.class).single();

        var b = builds.fromParams(Map.of("cpu", String.valueOf(cpu), "gpu", String.valueOf(cpu),
                "ram", "abc", "ssd", "999999", "x", "1", "psu", "-5"));
        assertThat(b.parts()).containsOnlyKeys(CPU);
        assertThat(builds.fromParams(Map.of("ram", ram)).has(RAM)).isTrue();

        assertThat(builds.fromParams(Map.of("cpu", retired)).isEmpty()).isTrue();
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

        assertThatThrownBy(() -> builds.deleteSaved(boris, saved.getId())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> builds.save(anna, "", Build.empty())).isInstanceOf(BadRequestException.class);
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
        return accounts.register(new RegisterRequest("Тест", "t-" + UUID.randomUUID() + "@example.com", "secret-123", true))
                .getAccountId();
    }

    private Build build(Object... slotsAndSlugs) {
        Build b = Build.empty();
        for (int i = 0; i < slotsAndSlugs.length; i += 2) {
            b = b.with((Slot) slotsAndSlugs[i], product((String) slotsAndSlugs[i + 1]));
        }
        return b;
    }

    private Product product(String slug) {
        return products.findAllByOrderByCategorySortOrderAscNameAsc().stream()
                .filter(p -> p.getSlug().equals(slug))
                .findFirst().orElseThrow();
    }

    private List<String> errors(Build b) {
        return compatibility.check(b).stream().filter(Issue::isError).map(Issue::text).toList();
    }
}
