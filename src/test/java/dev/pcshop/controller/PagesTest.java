package dev.pcshop.controller;

import dev.pcshop.TestcontainersConfig;
import dev.pcshop.dto.request.CustomerRequest;
import dev.pcshop.dto.request.OrderLineRequest;
import dev.pcshop.entity.Order;
import dev.pcshop.enums.Channel;
import dev.pcshop.repository.ProductRepository;
import dev.pcshop.service.CatalogService;
import dev.pcshop.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Смоук-тест страниц: каждая рендерится без ошибок шаблонов и показывает ключевой текст. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class PagesTest {

    private static final String ADMIN = "Basic " + Base64.getEncoder().encodeToString("admin:admin-test-pass".getBytes());

    @LocalServerPort int port;
    @Autowired OrderService orders;
    @Autowired CatalogService catalog;
    @Autowired ProductRepository products;

    private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

    @Test
    void storefrontPagesRender() throws Exception {
        assertThat(get("/", null).body()).contains("Сборка", "Процессоры");
        assertThat(get("/c/cpu?socket=AM5&sort=price_asc", null).body()).contains("AM5", "Сбросить фильтры");
        assertThat(get("/c/ram?page=1", null).statusCode()).isEqualTo(200);
        assertThat(get("/p/kingston-fury-beast-white-32-gb-2x16-ddr5-6000-cl36", null).body()).contains("67 990 ₽");
        assertThat(get("/cart", null).body()).contains("data-order-form");
        assertThat(get("/c/nope", null).statusCode()).isEqualTo(404);
    }

    @Test
    void sitePaymentMarksOrderPaid() throws Exception {
        var order = newOrder("Покупатель");

        var pay = get("/pay/" + order.getPublicId(), null);
        assertThat(pay.body()).contains("Оплатить", "Номер карты", "4242 4242 4242 4242", "Товар зарезервирован до");
        String expiry = YearMonth.now().plusYears(2).format(DateTimeFormatter.ofPattern("MM/yy"));
        var paid = post("/pay/" + order.getPublicId(), "number=4242+4242+4242+4242&expiry=" + expiry.replace("/", "%2F")
                + "&cvc=123&holder=&_csrf=" + csrf(pay.body()), null);
        assertThat(paid.statusCode()).isEqualTo(302);

        assertThat(get("/order/" + order.getPublicId(), null).body())
                .contains("Оплачен", "Kingston NV3", "Оплачено картой Visa •• 4242");
        assertThat(get("/admin/orders/" + order.getId(), ADMIN).body()).contains("Visa •• 4242");
    }

    @Test
    void adminRequiresLoginAndRenders() throws Exception {
        assertThat(get("/admin/orders", null).statusCode()).isEqualTo(401);

        var order = newOrder("Админ-тест");
        assertThat(get("/admin/orders", ADMIN).body()).contains("Админ-тест");
        assertThat(get("/admin/orders?status=AWAITING_PAYMENT", ADMIN).statusCode()).isEqualTo(200);

        var page = get("/admin/orders/" + order.getId(), ADMIN);
        assertThat(page.body()).contains(order.number());
        var changed = post("/admin/orders/" + order.getId() + "/status",
                "status=CANCELLED&_csrf=" + csrf(page.body()), ADMIN);
        assertThat(changed.statusCode()).isEqualTo(302);
        assertThat(get("/admin/orders/" + order.getId(), ADMIN).body()).contains("Отменён");

        assertThat(get("/admin/products", ADMIN).body()).contains("Kingston");
    }

    @Test
    void adminValidatesProductUpdate() throws Exception {
        var product = catalog.product("corsair-rm850x-2024-850-vt");
        String token = csrf(get("/admin/products", ADMIN).body());
        String url = "/admin/products/" + product.getId();

        var invalid = post(url, "price=-5&stock=1&active=true&_csrf=" + token, ADMIN);
        assertThat(invalid.statusCode()).isEqualTo(302);
        assertThat(catalog.product("corsair-rm850x-2024-850-vt").getPrice()).isEqualTo(product.getPrice());

        post(url, "price=4590&stock=7&active=true&_csrf=" + token, ADMIN);
        var updated = catalog.product("corsair-rm850x-2024-850-vt");
        assertThat(updated.getPrice()).isEqualTo(4590);
        assertThat(updated.getStock()).isEqualTo(7);

        // Снятая галочка «Продаётся» не приходит в запросе — товар скрывается
        post(url, "price=4590&stock=7&_csrf=" + token, ADMIN);
        assertThat(products.findById(product.getId()).orElseThrow().isActive()).isFalse();
        post(url, "price=4590&stock=7&active=true&_csrf=" + token, ADMIN);
        assertThat(products.findById(product.getId()).orElseThrow().isActive()).isTrue();
    }

    private Order newOrder(String name) {
        var ssd = catalog.product("kingston-nv3-1-tb-m-2-nvme");
        return orders.create(Channel.SITE, new CustomerRequest(name, "+7 900 000-00-00", "Москва", null),
                List.of(new OrderLineRequest(ssd.getId(), 1)), null);
    }

    private static String csrf(String html) {
        var m = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(html);
        assertThat(m.find()).as("CSRF-токен в форме").isTrue();
        return m.group(1);
    }

    private HttpResponse<String> get(String path, String auth) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (auth != null) b.header("Authorization", auth);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> post(String path, String form, String auth) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form));
        if (auth != null) b.header("Authorization", auth);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
