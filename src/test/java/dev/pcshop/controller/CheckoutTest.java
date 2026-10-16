package dev.pcshop.controller;

import dev.pcshop.TestMailbox;
import dev.pcshop.TestcontainersConfig;
import dev.pcshop.dto.request.CustomerRequest;
import dev.pcshop.dto.request.OrderLineRequest;
import dev.pcshop.dto.request.RegisterRequest;
import dev.pcshop.entity.PickupPoint;
import dev.pcshop.enums.Channel;
import dev.pcshop.enums.DeliveryMethod;
import dev.pcshop.enums.OrderStatus;
import dev.pcshop.exception.ResourceNotFoundException;
import dev.pcshop.repository.ProductRepository;
import dev.pcshop.service.AccountService;
import dev.pcshop.service.CatalogService;
import dev.pcshop.service.DeliveryService;
import dev.pcshop.service.OrderService;
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
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class CheckoutTest {

    @LocalServerPort int port;
    @Autowired CatalogService catalog;
    @Autowired OrderService orders;
    @Autowired DeliveryService delivery;
    @Autowired ProductRepository products;
    @Autowired TestMailbox mailbox;
    @Autowired AccountService accounts;

    final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

    @Test
    void guestPicksUpInStoreAndGetsLetters() throws Exception {
        var ssd = catalog.product("kingston-nv3-1-tb-m-2-nvme");
        PickupPoint store = delivery.pickupPoints().getFirst();
        String email = "guest-" + UUID.randomUUID() + "@example.com";
        String order = """
                {"customer": {"name": "Ольга", "phone": "+7 900 222-33-44", "address": "подменённый адрес", "email": "%s"},
                 "items": [{"productId": %d, "quantity": 1}], "delivery": "PICKUP", "pickupPointId": %d%s}""";

        var noConsent = postJson("/api/orders", order.formatted(email, ssd.getId(), store.getId(), ""));
        assertThat(noConsent.statusCode()).isEqualTo(400);
        assertThat(noConsent.body()).contains("Нужно согласие на обработку персональных данных");

        var created = postJson("/api/orders", order.formatted(email, ssd.getId(), store.getId(), ", \"consent\": true"));
        assertThat(created.statusCode()).isEqualTo(201);
        var saved = orders.byPublicId(UUID.fromString(find(created.body(), "\"publicId\":\"([^\"]+)\"")));
        assertThat(saved.getDeliveryMethod()).isEqualTo(DeliveryMethod.PICKUP);
        assertThat(saved.getAddress()).isEqualTo(store.oneLine());
        assertThat(saved.getDeliveryPrice()).isZero();
        assertThat(saved.getTotal()).isEqualTo(ssd.getPrice());
        assertThat(saved.getEmail()).isEqualTo(email);

        var placed = awaitLetter(email, 1);
        assertThat(placed.subject()).isEqualTo("Заказ " + saved.number() + " оформлен");
        assertThat(placed.text()).contains("Ольга", ssd.getName(), "Доставка: бесплатно", store.oneLine(),
                "http://localhost:8080/pay/" + saved.getPublicId());

        orders.markPaid(saved.getPublicId());
        assertThat(awaitLetter(email, 2).subject()).isEqualTo("Заказ " + saved.number() + " оплачен");

        orders.changeStatus(saved.getId(), OrderStatus.SHIPPED);
        var ready = awaitLetter(email, 3);
        assertThat(ready.subject()).isEqualTo("Заказ " + saved.number() + ": ждёт в магазине");
        assertThat(ready.text()).contains("ждёт вас в магазине", store.oneLine());
        assertThat(get("/order/" + saved.getPublicId()).body()).contains("Ждёт в магазине", "Самовывоз из магазина")
                .doesNotContain("Отменить заказ");
    }

    @Test
    void guestCancelsOrderAndStockReturns() throws Exception {
        var ram = catalog.product("kingston-fury-beast-white-32-gb-2x16-ddr5-6000-cl36");
        int before = products.findById(ram.getId()).orElseThrow().getStock();
        String email = "cancel-" + UUID.randomUUID() + "@example.com";
        var created = postJson("/api/orders", """
                {"customer": {"name": "Ольга", "phone": "+7 900 222-33-44", "address": "Москва, ул. Ленина, 1", "email": "%s"},
                 "items": [{"productId": %d, "quantity": 2}], "consent": true}""".formatted(email, ram.getId()));
        assertThat(created.statusCode()).isEqualTo(201);
        String publicId = find(created.body(), "\"publicId\":\"([^\"]+)\"");
        assertThat(products.findById(ram.getId()).orElseThrow().getStock()).isEqualTo(before - 2);

        var page = get("/order/" + publicId).body();
        assertThat(page).contains("Доставка курьером", "Отменить заказ", "Повторить заказ")
                .contains("data-repeat=\"" + ram.getId() + ":2\"");

        var cancelled = postForm("/order/" + publicId + "/cancel", "/order/" + publicId);
        assertThat(cancelled.headers().firstValue("Location").orElseThrow()).endsWith("/order/" + publicId);
        assertThat(orders.byPublicId(UUID.fromString(publicId)).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(products.findById(ram.getId()).orElseThrow().getStock()).isEqualTo(before);
        assertThat(get("/order/" + publicId).body()).contains("Заказ отменён", "товар вернулся на склад").doesNotContain("Отменить заказ");

        await().atMost(Duration.ofSeconds(5)).until(() -> mailbox.to(email).stream().anyMatch(l -> l.subject().endsWith(": отменён")));
    }

    @Test
    void shippedOrderCannotBeCancelledByCustomer() throws Exception {
        var ssd = catalog.product("kingston-nv3-1-tb-m-2-nvme");
        var created = postJson("/api/orders", """
                {"customer": {"name": "Ольга", "phone": "+7 900 222-33-44", "address": "Москва, ул. Ленина, 1"},
                 "items": [{"productId": %d, "quantity": 1}], "consent": true}""".formatted(ssd.getId()));
        var order = orders.byPublicId(UUID.fromString(find(created.body(), "\"publicId\":\"([^\"]+)\"")));
        assertThat(order.getDeliveryPrice()).isEqualTo(490);
        assertThat(order.getTotal()).isEqualTo(ssd.getPrice() + 490);

        orders.markPaid(order.getPublicId());
        orders.changeStatus(order.getId(), OrderStatus.SHIPPED);
        String page = "/order/" + order.getPublicId();
        assertThat(get(page).body()).doesNotContain("Отменить заказ");
        var attempt = postForm(page + "/cancel", page);
        assertThat(attempt.statusCode()).isEqualTo(302);
        assertThat(get(page).body()).contains("отменить его на сайте нельзя");
        assertThat(orders.byPublicId(order.getPublicId()).getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void accountOrderIsCancelledOnlyByOwner() {
        long owner = accounts.register(new RegisterRequest("Анна", "o-" + UUID.randomUUID() + "@example.com", "secret-123", true)).getAccountId();
        long ssd = catalog.product("kingston-nv3-1-tb-m-2-nvme").getId();
        var order = orders.create(Channel.SITE, new CustomerRequest("Анна", "+7 900 000-00-00", "Москва, ул. Ленина, 1", null),
                DeliveryMethod.COURIER, null, List.of(new OrderLineRequest(ssd, 1)), null, owner);

        assertThatThrownBy(() -> orders.cancelByCustomer(order.getPublicId(), null)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> orders.cancelByCustomer(order.getPublicId(), owner + 1000)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(orders.cancelByCustomer(order.getPublicId(), owner).getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void infoPagesAreLinkedFromFooter() throws Exception {
        var home = get("/").body();
        assertThat(home).contains("href=\"/delivery\"", "href=\"/warranty\"", "href=\"/contacts\"");

        var deliveryPage = get("/delivery");
        assertThat(deliveryPage.statusCode()).isEqualTo(200);
        assertThat(deliveryPage.body()).contains("Самовывоз из магазина", "490 ₽", "50 000 ₽", delivery.pickupPoints().getFirst().getAddress());
        assertThat(get("/warranty").body()).contains("Гарантия и возврат", "в течение 7 дней");
        assertThat(get("/contacts").body()).contains("8 800 000-00-00", "ежедневно 10:00–22:00");
        assertThat(get("/privacy").body()).contains("Какие данные мы храним");

        var cart = get("/cart").body();
        assertThat(cart).contains("name=\"delivery\" value=\"PICKUP\"", "name=\"pickupPointId\"", "name=\"consent\"", "href=\"/privacy\"");
    }

    @Test
    void registrationNeedsConsent() throws Exception {
        var r = postForm("/register", "/register", "name", "Анна", "email", "c-" + UUID.randomUUID() + "@example.com",
                "password", "secret-123");
        assertThat(r.statusCode()).as("форма с ошибкой, а не вход").isEqualTo(200);
        assertThat(r.body()).contains("Нужно согласие на обработку персональных данных");
    }

    private TestMailbox.Letter awaitLetter(String email, int count) {
        await().atMost(Duration.ofSeconds(5)).until(() -> mailbox.to(email).size() >= count);
        return mailbox.to(email).get(count - 1);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(path)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(String path, String json) throws Exception {
        String cart = get("/cart").body();
        return http.send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .header(find(cart, "name=\"csrf-header\" content=\"([^\"]+)\""), find(cart, "name=\"csrf-token\" content=\"([^\"]+)\""))
                .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postForm(String path, String pageWithForm, String... pairs) throws Exception {
        String page = get(pageWithForm).body();
        var body = new StringBuilder("_csrf=").append(enc(find(page, "name=\"csrf-token\" content=\"([^\"]+)\"")));
        for (int i = 0; i < pairs.length; i += 2) {
            body.append('&').append(pairs[i]).append('=').append(enc(pairs[i + 1]));
        }
        return http.send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String find(String text, String regex) {
        var m = Pattern.compile(regex).matcher(text);
        assertThat(m.find()).as("ищем " + regex).isTrue();
        return m.group(1);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
