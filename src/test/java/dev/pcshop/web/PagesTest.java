package dev.pcshop.web;

import dev.pcshop.TestcontainersConfig;
import dev.pcshop.catalog.CatalogService;
import dev.pcshop.order.Channel;
import dev.pcshop.order.Customer;
import dev.pcshop.order.OrderLine;
import dev.pcshop.order.OrderService;
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
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Смоук-тест страниц: каждая рендерится без ошибок шаблонов и показывает ключевой текст. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class PagesTest {

    private static final String ADMIN = "Basic " + Base64.getEncoder().encodeToString("admin:admin".getBytes());

    @LocalServerPort int port;
    @Autowired OrderService orders;
    @Autowired CatalogService catalog;

    private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

    @Test
    void storefrontPagesRender() throws Exception {
        assertThat(get("/", null).body()).contains("Сборка", "Процессоры");
        assertThat(get("/c/cpu?socket=AM5&sort=price_asc", null).body()).contains("AM5", "Сбросить фильтры");
        assertThat(get("/c/ram?page=1", null).statusCode()).isEqualTo(200);
        assertThat(get("/p/kingston-fury-beast-32-gb-2x16-ddr5-6000", null).body()).contains("9 490 ₽");
        assertThat(get("/cart", null).body()).contains("data-order-form");
        assertThat(get("/c/nope", null).statusCode()).isEqualTo(404);
    }

    @Test
    void sitePaymentMarksOrderPaid() throws Exception {
        var order = newOrder("Покупатель");

        var pay = get("/pay/" + order.getPublicId(), null);
        assertThat(pay.body()).contains("Оплатить");
        var paid = post("/pay/" + order.getPublicId(), "_csrf=" + csrf(pay.body()), null);
        assertThat(paid.statusCode()).isEqualTo(302);

        assertThat(get("/order/" + order.getPublicId(), null).body()).contains("Оплачен", "Kingston NV3");
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

    private dev.pcshop.order.Order newOrder(String name) {
        var ssd = catalog.product("kingston-nv3-1-tb");
        return orders.create(Channel.SITE, new Customer(name, "+7 900 000-00-00", "Москва", null),
                List.of(new OrderLine(ssd.getId(), 1)), null);
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
