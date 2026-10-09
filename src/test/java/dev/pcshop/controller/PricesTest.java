package dev.pcshop.controller;

import dev.pcshop.TestMailbox;
import dev.pcshop.TestcontainersConfig;
import dev.pcshop.dto.request.CustomerRequest;
import dev.pcshop.dto.request.OrderLineRequest;
import dev.pcshop.dto.request.ProductUpdateRequest;
import dev.pcshop.entity.Product;
import dev.pcshop.enums.Channel;
import dev.pcshop.enums.OrderStatus;
import dev.pcshop.repository.AccountRepository;
import dev.pcshop.repository.ProductRepository;
import dev.pcshop.service.CatalogService;
import dev.pcshop.service.OrderService;
import dev.pcshop.service.StockAlertService;
import dev.pcshop.util.Formats;
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
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Цены: скидки на товары, промокоды в корзине и письмо «Снова в наличии». */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class PricesTest {

    private static final String ADMIN = "Basic " + Base64.getEncoder().encodeToString("admin:admin-test-pass".getBytes());

    @LocalServerPort int port;
    @Autowired CatalogService catalog;
    @Autowired OrderService orders;
    @Autowired ProductRepository products;
    @Autowired AccountRepository accounts;
    @Autowired StockAlertService stockAlerts;
    @Autowired TestMailbox mailbox;

    /** Отдельный «браузер» со своими cookie. */
    class Browser {
        final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

        HttpResponse<String> get(String path, String auth) throws Exception {
            var b = HttpRequest.newBuilder(uri(path));
            if (auth != null) b.header("Authorization", auth);
            return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> get(String path) throws Exception {
            return get(path, null);
        }

        /** Форма с CSRF-токеном со страницы pageWithToken: мета-тег на витрине, скрытое поле в админке. */
        HttpResponse<String> post(String path, String pageWithToken, String auth, String... pairs) throws Exception {
            String page = get(pageWithToken, auth).body();
            String token = auth == null ? find(page, "name=\"csrf-token\" content=\"([^\"]+)\"") : find(page, "name=\"_csrf\" value=\"([^\"]+)\"");
            var body = new StringBuilder("_csrf=").append(enc(token));
            for (int i = 0; i < pairs.length; i += 2) {
                body.append('&').append(pairs[i]).append('=').append(enc(pairs[i + 1]));
            }
            var b = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
            if (auth != null) b.header("Authorization", auth);
            return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> postJson(String path, String json) throws Exception {
            String cart = get("/cart").body();
            return http.send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                    .header(find(cart, "name=\"csrf-header\" content=\"([^\"]+)\""), find(cart, "name=\"csrf-token\" content=\"([^\"]+)\""))
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
        }

        /** Регистрирует покупателя и возвращает его email. */
        String register() throws Exception {
            String email = "prices-" + UUID.randomUUID() + "@example.com";
            var r = post("/register", "/register", null, "name", "Ирина", "email", email, "password", "secret-123", "consent", "true");
            assertThat(r.statusCode()).isEqualTo(302);
            return email;
        }
    }

    @Test
    void discountIsShownEverywhereAndEditedInAdmin() throws Exception {
        // Скидка из демо-данных: 32 990 → 28 990 ₽, это 12%
        var cpu = catalog.product("amd-ryzen-7-7800x3d-oem");
        assertThat(cpu.onSale()).isTrue();
        assertThat(cpu.discountPercent()).isEqualTo(12);

        var b = new Browser();
        assertThat(b.get("/p/" + cpu.getSlug()).body()).contains("price--sale", "−12%", Formats.price(cpu.getOldPrice()));
        assertThat(b.get("/c/cpu?min=28000&max=30000").body()).contains("sale-badge", Formats.price(cpu.getOldPrice()));
        assertThat(b.get("/").body()).contains("href=\"/sale\"", "Все скидки");
        var sale = b.get("/sale").body();
        assertThat(sale).contains("<h1>Скидки</h1>", cpu.getName()).doesNotContain(catalog.product("amd-ryzen-5-7600-oem").getName());
        assertThat(b.get("/api/products/by-ids?ids=" + cpu.getId()).body()).contains("\"oldPrice\":" + cpu.getOldPrice());

        // Админка: цена до скидки должна быть больше новой, пустое поле убирает скидку
        Product cooler = catalog.product("arctic-freezer-36-a-rgb-white");
        String url = "/admin/products/" + cooler.getId();
        String stock = String.valueOf(cooler.getStock());
        b.post(url, "/admin/products", ADMIN, "price", "4190", "oldPrice", "3990", "stock", stock, "active", "true");
        assertThat(b.get("/admin/products", ADMIN).body()).contains("Цена до скидки должна быть больше новой цены");
        assertThat(catalog.product(cooler.getSlug()).onSale()).isFalse();

        b.post(url, "/admin/products", ADMIN, "price", "3990", "oldPrice", "4790", "stock", stock, "active", "true");
        var discounted = catalog.product(cooler.getSlug());
        assertThat(discounted.getPrice()).isEqualTo(3990);
        assertThat(discounted.getOldPrice()).isEqualTo(4790);
        assertThat(b.get("/sale").body()).contains(cooler.getName());

        b.post(url, "/admin/products", ADMIN, "price", "4190", "oldPrice", "", "stock", stock, "active", "true");
        assertThat(catalog.product(cooler.getSlug()).getOldPrice()).isNull();
    }

    @Test
    void promoCodeDiscountsItemsButNotDelivery() throws Exception {
        var b = new Browser();
        var promo = b.get("/api/promo/%20sborka10%20");
        assertThat(promo.statusCode()).isEqualTo(200);
        assertThat(promo.body()).contains("\"code\":\"SBORKA10\"", "\"percent\":10", "−10% на заказ от 30 000 ₽");
        assertThat(b.get("/api/promo/NOPE").body()).contains("Такого промокода нет");
        var expired = b.get("/api/promo/leto2026");
        assertThat(expired.statusCode()).isEqualTo(400);
        assertThat(expired.body()).contains("Срок действия промокода LETO2026 закончился");

        // Товар с большим остатком, который не трогают другие тесты
        var cpu = catalog.product("intel-core-i3-12100f-oem");
        int stock = cpu.getStock();
        String email = "promo-" + UUID.randomUUID() + "@example.com";
        String order = """
                {"customer": {"name": "Ирина", "phone": "+7 900 222-33-44", "address": "Москва, ул. Ленина, 1", "email": "%s"},
                 "items": [{"productId": %d, "quantity": %d}], "consent": true, "promoCode": "sborka10"}""";

        var small = b.postJson("/api/orders", order.formatted(email, cpu.getId(), 2));
        assertThat(small.statusCode()).isEqualTo(400);
        assertThat(small.body()).contains("Промокод SBORKA10 действует на заказ от 30 000 ₽");
        assertThat(products.findById(cpu.getId()).orElseThrow().getStock()).as("резерв откатился").isEqualTo(stock);

        var created = b.postJson("/api/orders", order.formatted(email, cpu.getId(), 5));
        assertThat(created.statusCode()).isEqualTo(201);
        var saved = orders.byPublicId(UUID.fromString(find(created.body(), "\"publicId\":\"([^\"]+)\"")));
        int items = cpu.getPrice() * 5;
        int discount = items / 10;
        assertThat(saved.getPromoCode()).isEqualTo("SBORKA10");
        assertThat(saved.getDiscount()).isEqualTo(discount);
        assertThat(saved.itemsTotal()).isEqualTo(items);
        // Бесплатная доставка — от суммы товаров до скидки, даже если со скидкой вышло меньше 50 000 ₽
        assertThat(items).isGreaterThanOrEqualTo(50_000);
        assertThat(items - discount).isLessThan(50_000);
        assertThat(saved.getDeliveryPrice()).isZero();
        assertThat(saved.getTotal()).isEqualTo(items - discount);

        assertThat(b.get("/order/" + saved.getPublicId()).body()).contains("Промокод SBORKA10", "−" + Formats.price(discount));
        await().atMost(Duration.ofSeconds(5)).until(() -> !mailbox.to(email).isEmpty());
        assertThat(mailbox.to(email).getFirst().text())
                .contains("Скидка по промокоду SBORKA10: −" + Formats.price(discount), "Итого: " + Formats.price(items - discount));
    }

    @Test
    void stockAlertLettersWhenProductIsBack() throws Exception {
        var gpu = catalog.product("msi-geforce-rtx-5080-gaming-trio-oc-16-gb");
        String page = "/p/" + gpu.getSlug();
        catalog.updateProduct(gpu.getId(), new ProductUpdateRequest(gpu.getPrice(), gpu.getOldPrice(), 0, true));

        assertThat(new Browser().get(page).body()).as("гостя просим войти")
                .contains("Сообщить о поступлении", "href=\"/login?next=" + page + "\"");

        var b = new Browser();
        String email = b.register();
        long accountId = accounts.findByEmail(email).orElseThrow().getId();
        b.post(page + "/stock-alert", page, null);
        assertThat(b.get(page).body()).contains("Готово, напишем, когда товар появится", "Не сообщать");
        assertThat(stockAlerts.subscribed(accountId, gpu.getId())).isTrue();

        // На товар в наличии подписаться нельзя
        String inStock = "/p/" + catalog.product("kingston-nv3-1-tb-m-2-nvme").getSlug();
        b.post(inStock + "/stock-alert", inStock, null);
        assertThat(b.get(inStock).body()).doesNotContain("Не сообщать");
        assertThat(stockAlerts.subscribed(accountId, catalog.product("kingston-nv3-1-tb-m-2-nvme").getId())).isFalse();

        // Привезли: письмо подписчику, подписка больше не нужна
        catalog.updateProduct(gpu.getId(), new ProductUpdateRequest(gpu.getPrice(), gpu.getOldPrice(), 2, true));
        await().atMost(Duration.ofSeconds(5)).until(() -> !mailbox.to(email).isEmpty());
        var letter = mailbox.to(email).getFirst();
        assertThat(letter.subject()).isEqualTo("Снова в наличии: " + gpu.getName());
        assertThat(letter.text()).contains("Ирина", Formats.price(gpu.getPrice()), "http://localhost:8080" + page);
        await().atMost(Duration.ofSeconds(5)).until(() -> !stockAlerts.subscribed(accountId, gpu.getId()));

        // Последний экземпляр в чужом заказе: после отмены заказа он вернулся на склад — тоже письмо
        var last = catalog.product("palit-geforce-rtx-5090-gamerock-32-gb");
        catalog.updateProduct(last.getId(), new ProductUpdateRequest(last.getPrice(), last.getOldPrice(), 1, true));
        var order = orders.create(Channel.SITE, new CustomerRequest("Пётр", "+7 900 000-00-00", "Москва", null),
                List.of(new OrderLineRequest(last.getId(), 1)), null);
        String lastPage = "/p/" + last.getSlug();
        b.post(lastPage + "/stock-alert", lastPage, null);
        assertThat(stockAlerts.subscribed(accountId, last.getId())).isTrue();
        orders.changeStatus(order.getId(), OrderStatus.CANCELLED);
        await().atMost(Duration.ofSeconds(5)).until(() -> mailbox.to(email).size() == 2);
        assertThat(mailbox.to(email).get(1).subject()).isEqualTo("Снова в наличии: " + last.getName());
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
