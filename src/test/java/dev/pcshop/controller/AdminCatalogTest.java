package dev.pcshop.controller;

import dev.pcshop.AdminForms;
import dev.pcshop.TestcontainersConfig;
import dev.pcshop.dto.request.CategoryRequest;
import dev.pcshop.dto.request.CustomerRequest;
import dev.pcshop.dto.request.OrderLineRequest;
import dev.pcshop.dto.request.ProductRequest;
import dev.pcshop.entity.Product;
import dev.pcshop.entity.Spec;
import dev.pcshop.entity.StockMovement;
import dev.pcshop.enums.Channel;
import dev.pcshop.enums.OrderStatus;
import dev.pcshop.enums.SpecType;
import dev.pcshop.enums.StockReason;
import dev.pcshop.exception.BadRequestException;
import dev.pcshop.repository.CategoryRepository;
import dev.pcshop.repository.ProductRepository;
import dev.pcshop.service.CatalogAdminService;
import dev.pcshop.service.CatalogService;
import dev.pcshop.service.OrderService;
import dev.pcshop.service.StockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class AdminCatalogTest {

    private static final String ADMIN = "Basic " + Base64.getEncoder().encodeToString("admin:admin-test-pass".getBytes());

    @LocalServerPort int port;
    @Autowired CatalogAdminService admin;
    @Autowired CatalogService catalog;
    @Autowired StockService stock;
    @Autowired OrderService orders;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;

    private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

    @Test
    void newCategoryAndProductAppearOnSite() throws Exception {
        var created = post("/admin/categories", "/admin/categories/new",
                "name", "Мониторы", "sortOrder", "20",
                "specs[0].label", "Диагональ, дюймы", "specs[0].type", "NUMBER", "specs[0].filter", "true",
                "specs[1].label", "Матрица", "specs[1].type", "TEXT",
                "specs[2].label", "", "specs[2].type", "TEXT");
        assertThat(created.statusCode()).isEqualTo(302);
        var monitors = categories.findBySlug("monitory").orElseThrow();
        assertThat(monitors.getSpecs()).containsExactly(
                new Spec("diagonal_dyuymy", "Диагональ, дюймы", SpecType.NUMBER, true),
                new Spec("matritsa", "Матрица", SpecType.TEXT, false));

        var product = post("/admin/products", "/admin/products/new?category=" + monitors.getId(),
                "categoryId", String.valueOf(monitors.getId()), "name", "Dell S2725DS", "brand", "Dell", "price", "25990",
                "stock", "4", "active", "true", "description", "QHD, 100 Гц",
                "specs[diagonal_dyuymy]", " 27 ", "specs[matritsa]", "IPS");
        assertThat(product.statusCode()).isEqualTo(302);
        Product dell = catalog.product("dell-s2725ds");
        assertThat(dell.getAttributes()).isEqualTo(Map.of("diagonal_dyuymy", 27, "matritsa", "IPS"));
        assertThat(dell.getStock()).isEqualTo(4);
        assertThat(stock.history(dell.getId())).singleElement()
                .satisfies(m -> assertThat(List.of(m.getReason(), m.getDelta(), m.getStockAfter(), m.getComment()))
                        .containsExactly(StockReason.RECEIPT, 4, 4, "Начальный остаток"));

        assertThat(get("/", null).body()).contains("href=\"/c/monitory\"");
        assertThat(get("/c/monitory", null).body()).contains("Dell S2725DS", "Диагональ, дюймы", "27 дюймы · IPS");
        assertThat(get("/c/monitory?diagonal_dyuymy=24", null).body()).doesNotContain("Dell S2725DS");
        assertThat(get("/p/dell-s2725ds", null).body()).contains("Матрица", "IPS", "25 990 ₽");

        var copy = post("/admin/products", "/admin/products/new?copy=" + dell.getId(), AdminForms.product(dell));
        assertThat(copy.statusCode()).isEqualTo(302);
        assertThat(products.existsBySlug("dell-s2725ds-2")).isTrue();

        assertThat(get("/admin/products?q=s2725", ADMIN).body()).contains("Dell S2725DS").doesNotContain("Ryzen");
        post("/admin/products/" + dell.getId(), "/admin/products/" + dell.getId(), AdminForms.product(dell, "active", null));
        assertThat(get("/admin/products?show=HIDDEN&q=dell", ADMIN).body()).contains("Dell S2725DS");
        assertThat(get("/admin/products?show=ACTIVE&q=dell", ADMIN).body()).contains("Dell S2725DS").doesNotContain("href=\"/admin/products/" + dell.getId() + "\"");
        assertThat(get("/p/dell-s2725ds", null).statusCode()).isEqualTo(404);

        post("/admin/categories/" + monitors.getId() + "/delete", "/admin/categories/" + monitors.getId());
        assertThat(categories.findById(monitors.getId())).isPresent();
        admin.createCategory(new CategoryRequest("Пустая", 99, List.of()));
        long empty = categories.findBySlug("pustaya").orElseThrow().getId();
        post("/admin/categories/" + empty + "/delete", "/admin/categories/" + empty);
        assertThat(categories.findById(empty)).isEmpty();
    }

    @Test
    void formShowsErrorsAndKeepsInput() throws Exception {
        Product psu = catalog.product("deepcool-pn750d-750-vt");
        String url = "/admin/products/" + psu.getId();

        var negative = post(url, url, AdminForms.product(psu, "price", "-5", "name", "Изменённое имя"));
        assertThat(negative.statusCode()).isEqualTo(200);
        assertThat(negative.body()).contains("Цена должна быть больше нуля", "value=\"Изменённое имя\"");

        var notNumber = post(url, url, AdminForms.product(psu, "specs[power]", "много"));
        assertThat(notNumber.body()).contains("«Мощность, Вт» — нужно целое число");

        var noPower = post(url, url, AdminForms.product(psu, "specs[power]", ""));
        assertThat(noPower.body()).contains("Конфигуратору сборок нужны: Мощность, Вт");

        var sale = post(url, url, AdminForms.product(psu, "price", "9000", "oldPrice", "8000"));
        assertThat(sale.body()).contains("Цена до скидки должна быть больше новой цены");
        assertThat(catalog.product(psu.getSlug()).getPrice()).isEqualTo(psu.getPrice());

        long cpu = catalog.category("cpu").getId();
        var noSocket = post("/admin/products", "/admin/products/new?category=" + cpu,
                "categoryId", String.valueOf(cpu), "name", "Без сокета", "brand", "AMD", "price", "1000", "active", "true");
        assertThat(noSocket.body()).contains("Конфигуратору сборок нужны: Сокет, Тип памяти, Тепловыделение, Вт");
        assertThat(products.existsBySlug("bez-soketa")).isFalse();

        var cpuCategory = catalog.category("cpu");
        var rows = new ArrayList<>(CategoryRequest.of(cpuCategory).specs());
        rows.set(0, new CategoryRequest.SpecRow("socket", "Сокет", SpecType.TEXT, true, true));
        assertThatThrownBy(() -> admin.updateCategory(cpuCategory.getId(), new CategoryRequest("Процессоры", 1, rows)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("нужна конфигуратору");
    }

    @Test
    void stockChangesOnlyThroughJournal() throws Exception {
        Product cooler = newProduct("Кулер для склада");
        String page = "/admin/products/" + cooler.getId();

        post(page + "/stock", page, "operation", "RECEIPT", "quantity", "10", "comment", "Накладная 7");
        assertThat(stockOf(cooler)).isEqualTo(10);
        var tooMuch = post(page + "/stock", page, "operation", "WRITE_OFF", "quantity", "25", "comment", "брак");
        assertThat(tooMuch.statusCode()).isEqualTo(302);
        assertThat(get(page, ADMIN).body()).contains("Столько нет: на складе 10 шт.");
        post(page + "/stock", page, "operation", "COUNT", "quantity", "8", "comment", "");
        assertThat(stockOf(cooler)).isEqualTo(8);
        post(page + "/stock", page, "operation", "SALE", "quantity", "1");
        assertThat(stockOf(cooler)).isEqualTo(8);

        var order = orders.create(Channel.SITE, new CustomerRequest("Склад", "+7 900 000-00-01", "Москва", null),
                List.of(new OrderLineRequest(cooler.getId(), 3)), null);
        assertThat(stockOf(cooler)).isEqualTo(5);
        orders.changeStatus(order.getId(), OrderStatus.CANCELLED);
        assertThat(stockOf(cooler)).isEqualTo(8);

        List<StockMovement> history = stock.history(cooler.getId());
        assertThat(history).extracting(StockMovement::getReason).containsExactly(
                StockReason.RETURN, StockReason.SALE, StockReason.COUNT, StockReason.RECEIPT);
        assertThat(history).extracting(StockMovement::getDelta).containsExactly(3, -3, -2, 10);
        assertThat(history).extracting(StockMovement::getStockAfter).containsExactly(8, 5, 8, 10);
        assertThat(history.get(1).getOrderId()).isEqualTo(order.getId());
        assertThat(get(page, ADMIN).body()).contains("Заказ " + order.number(), "Накладная 7", "Возврат из заказа");
    }

    @Test
    void savingProductDoesNotOverwriteSaleMadeMeanwhile() {
        Product ram = newProduct("Память для гонки");
        stock.receive(ram.getId(), 5, null);

        Product opened = admin.product(ram.getId());
        orders.create(Channel.SITE, new CustomerRequest("Гонка", "+7 900 000-00-02", "Москва", null),
                List.of(new OrderLineRequest(ram.getId(), 2)), null);
        var form = ProductRequest.of(opened);
        admin.update(ram.getId(), new ProductRequest(form.categoryId(), form.name(), form.brand(), 4990, null,
                form.description(), true, form.specs(), null));

        Product saved = products.findById(ram.getId()).orElseThrow();
        assertThat(saved.getPrice()).isEqualTo(4990);
        assertThat(saved.getStock()).as("продажа не потерялась").isEqualTo(3);
    }

    private Product newProduct(String name) {
        long cooler = catalog.category("cooler").getId();
        var created = admin.create(new ProductRequest(cooler, name + " " + UUID.randomUUID().toString().substring(0, 8), "Test",
                3990, null, "", true, Map.of("sockets", "AM5", "cooler_type", "Башенный"), null));
        return admin.product(created.getId());
    }

    private int stockOf(Product p) {
        return products.findById(p.getId()).orElseThrow().getStock();
    }

    private HttpResponse<String> get(String path, String auth) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (auth != null) {
            b.header("Authorization", auth);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String pageWithForm, String... pairs) throws Exception {
        var m = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(get(pageWithForm, ADMIN).body());
        assertThat(m.find()).as("CSRF на " + pageWithForm).isTrue();
        var body = new StringBuilder("_csrf=").append(enc(m.group(1)));
        for (int i = 0; i < pairs.length; i += 2) {
            body.append('&').append(enc(pairs[i])).append('=').append(enc(pairs[i + 1]));
        }
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", ADMIN).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
