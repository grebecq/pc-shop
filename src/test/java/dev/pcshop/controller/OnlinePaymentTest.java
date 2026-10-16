package dev.pcshop.controller;

import dev.pcshop.FakeYooKassa;
import dev.pcshop.TestMailbox;
import dev.pcshop.TestcontainersConfig;
import dev.pcshop.dto.request.CustomerRequest;
import dev.pcshop.dto.request.OrderLineRequest;
import dev.pcshop.entity.Order;
import dev.pcshop.enums.Channel;
import dev.pcshop.enums.OrderStatus;
import dev.pcshop.service.CatalogService;
import dev.pcshop.service.OrderService;
import dev.pcshop.service.PaymentService;
import dev.pcshop.util.Formats;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

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
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Настоящая оплата через ЮKassa, только вместо её API — FakeYooKassa.
 * Ключи заданы, поэтому у этих тестов свой контекст Spring и своя база.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"shop.yookassa.shop-id=" + FakeYooKassa.SHOP_ID, "shop.yookassa.secret-key=" + FakeYooKassa.SECRET_KEY})
@Import(TestcontainersConfig.class)
class OnlinePaymentTest {

    private static final String ADMIN = "Basic " + Base64.getEncoder().encodeToString("admin:admin-test-pass".getBytes());
    private static final FakeYooKassa YOOKASSA = new FakeYooKassa();

    @DynamicPropertySource
    static void yookassa(DynamicPropertyRegistry registry) {
        registry.add("shop.yookassa.api-url", YOOKASSA::apiUrl);
    }

    @LocalServerPort int port;
    @Autowired OrderService orders;
    @Autowired PaymentService payments;
    @Autowired CatalogService catalog;
    @Autowired TestMailbox mailbox;

    private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

    @Test
    void payButtonSendsBuyerToYookassaOnce() throws Exception {
        Order order = newOrder();
        String page = get("/pay/" + order.getPublicId()).body();
        assertThat(page).contains("Оплата онлайн", "Оплатить " + Formats.price(order.getTotal())).doesNotContain("name=\"number\"", "Тестовая оплата");

        var redirect = startPayment(order);
        assertThat(redirect.statusCode()).isEqualTo(302);
        String paymentId = orders.byPublicId(order.getPublicId()).getPaymentId();
        assertThat(redirect.headers().firstValue("Location").orElseThrow()).isEqualTo("https://yoomoney.test/checkout?orderId=" + paymentId);

        var create = YOOKASSA.calls("POST", "/payments").stream().filter(c -> metadata(c.body()).equals(order.getPublicId().toString())).toList();
        assertThat(create).hasSize(1);
        assertThat(create.getFirst().idempotenceKey()).isNotBlank();
        assertThat(create.getFirst().body()).containsEntry("capture", true)
                .containsEntry("amount", Map.of("value", order.getTotal() + ".00", "currency", "RUB"))
                .containsEntry("description", "Заказ " + order.number());
        @SuppressWarnings("unchecked")
        var confirmation = (Map<String, Object>) create.getFirst().body().get("confirmation");
        assertThat(confirmation).containsEntry("type", "redirect")
                .containsEntry("return_url", "http://localhost:8080/order/" + order.getPublicId() + "?paid");

        // Повторный клик (или «Назад» в браузере) ведёт на тот же платёж, второй не создаётся
        assertThat(startPayment(order).headers().firstValue("Location").orElseThrow()).endsWith(paymentId);
        assertThat(orders.byPublicId(order.getPublicId()).getPaymentId()).isEqualTo(paymentId);
        assertThat(YOOKASSA.payment(paymentId)).isNotNull();
    }

    @Test
    void buyerReturnsFromYookassaAndSeesPaidOrder() throws Exception {
        Order order = newOrder();
        String paymentId = pay(order);
        // Пока не заплатил — честно говорим, что оплата не подтверждена
        assertThat(get("/order/" + order.getPublicId() + "?paid").body()).contains("Оплата ещё не подтверждена").doesNotContain("Оплата прошла");

        YOOKASSA.succeed(paymentId);
        // Уведомление ещё не пришло, но страница заказа сама спросила ЮKassa
        assertThat(get("/order/" + order.getPublicId() + "?paid").body()).contains("Оплата прошла", "Оплачено: Visa •• 4242");
        Order paid = orders.byPublicId(order.getPublicId());
        assertThat(paid.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paid.getPaymentCard()).isEqualTo("Visa •• 4242");
        assertThat(paid.paidOnline()).isTrue();
        // На оплату больше не пускает
        assertThat(get("/pay/" + order.getPublicId()).statusCode()).isEqualTo(302);
    }

    @Test
    void webhookPaysOrderButBodyIsNotTrusted() throws Exception {
        Order order = newOrder();
        String paymentId = pay(order);

        // Уведомление «оплачено», а API говорит pending: верим API
        assertThat(webhook("payment.succeeded", paymentId).statusCode()).isEqualTo(200);
        assertThat(orders.byPublicId(order.getPublicId()).getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        // Чужие и выдуманные id — просто 200, чтобы ЮKassa не повторяла
        assertThat(webhook("payment.succeeded", "2f5e8a1c-000f-5000-9000-1b2c3d4e5f60").statusCode()).isEqualTo(200);
        assertThat(webhook("payment.succeeded", "../me").statusCode()).isEqualTo(200);

        // Без CSRF-токена: уведомление шлёт сервер ЮKassa, а не браузер
        YOOKASSA.succeed(paymentId);
        assertThat(webhook("payment.succeeded", paymentId).statusCode()).isEqualTo(200);
        assertThat(orders.byPublicId(order.getPublicId()).getStatus()).isEqualTo(OrderStatus.PAID);
        // Повтор уведомления ничего не меняет и денег не возвращает
        assertThat(webhook("payment.succeeded", paymentId).statusCode()).isEqualTo(200);
        assertThat(refunds(paymentId)).isEmpty();
    }

    @Test
    void webhookAsksToRetryWhenYookassaIsDown() throws Exception {
        Order order = newOrder();
        String paymentId = pay(order);
        YOOKASSA.succeed(paymentId);
        YOOKASSA.setDown(true);
        try {
            assertThat(webhook("payment.succeeded", paymentId).statusCode()).isEqualTo(503);
            // И автоотмена ждёт: вдруг заказ уже оплачен
            assertThat(payments.mayExpire(order.getId())).isFalse();
        } finally {
            YOOKASSA.setDown(false);
        }
        assertThat(payments.mayExpire(order.getId())).isFalse();
        assertThat(orders.byPublicId(order.getPublicId()).getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void paymentAfterCancellationIsRefunded() throws Exception {
        Order order = newOrder();
        String paymentId = pay(order);
        orders.cancelByCustomer(order.getPublicId(), null);

        // Покупатель всё-таки заплатил на открытой странице ЮKassa
        YOOKASSA.succeed(paymentId);
        assertThat(webhook("payment.succeeded", paymentId).statusCode()).isEqualTo(200);

        var refunds = refunds(paymentId);
        assertThat(refunds).hasSize(1);
        assertThat(refunds.getFirst().body()).containsEntry("amount", Map.of("value", order.getTotal() + ".00", "currency", "RUB"));
        assertThat(refunds.getFirst().idempotenceKey()).isNotBlank();
        Order cancelled = orders.byPublicId(order.getPublicId());
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.getRefundedAt()).isNotNull();
        assertThat(get("/order/" + order.getPublicId()).body()).contains("Деньги вернутся");

        // Повтор уведомления второй раз деньги не возвращает
        webhook("payment.succeeded", paymentId);
        assertThat(refunds(paymentId)).hasSize(1);
    }

    @Test
    void cancellingPaidOrderRefundsAutomatically() throws Exception {
        Order order = newOrder();
        String paymentId = pay(order);
        YOOKASSA.succeed(paymentId);
        webhook("payment.succeeded", paymentId);

        orders.changeStatus(order.getId(), OrderStatus.CANCELLED);
        await().atMost(Duration.ofSeconds(5)).until(() -> orders.byId(order.getId()).getRefundedAt() != null);
        assertThat(refunds(paymentId)).hasSize(1);
        assertThat(get("/admin/orders/" + order.getId(), ADMIN).body())
                .contains("ЮKassa, платёж " + paymentId, "Деньги возвращены").doesNotContain("Вернуть деньги");
    }

    @Test
    void adminCanRetryFailedRefund() throws Exception {
        Order order = newOrder();
        String paymentId = pay(order);
        YOOKASSA.succeed(paymentId);
        webhook("payment.succeeded", paymentId);

        int checks = YOOKASSA.calls("GET", "/payments/" + paymentId).size();
        YOOKASSA.setDown(true);
        try {
            orders.changeStatus(order.getId(), OrderStatus.CANCELLED);
            // Автоматический возврат упёрся в недоступную ЮKassa
            await().atMost(Duration.ofSeconds(5)).until(() -> YOOKASSA.calls("GET", "/payments/" + paymentId).size() > checks);
        } finally {
            YOOKASSA.setDown(false);
        }
        String page = get("/admin/orders/" + order.getId(), ADMIN).body();
        assertThat(page).contains("деньги покупателю ещё не вернулись", "Вернуть деньги");

        var r = post("/admin/orders/" + order.getId() + "/refund", "/admin/orders/" + order.getId(), ADMIN);
        assertThat(r.statusCode()).isEqualTo(302);
        assertThat(refunds(paymentId)).hasSize(1);
        assertThat(orders.byId(order.getId()).getRefundedAt()).isNotNull();
    }

    @Test
    void demoCardFormIsOff() throws Exception {
        Order order = newOrder();
        String token = csrf(get("/pay/" + order.getPublicId()).body());
        var r = send(HttpRequest.newBuilder(uri("/pay/" + order.getPublicId()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("_csrf=" + enc(token) + "&number=4242424242424242&expiry=12%2F40&cvc=123&holder=")));
        assertThat(r.statusCode()).isEqualTo(302);
        assertThat(r.headers().firstValue("Location").orElseThrow()).endsWith("/pay/" + order.getPublicId());
        assertThat(orders.byPublicId(order.getPublicId()).getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
    }

    @Test
    void settingsPageChecksKeysAndSendsTestMail() throws Exception {
        String page = get("/admin/settings", ADMIN).body();
        assertThat(page).contains("подключена, shopId " + FakeYooKassa.SHOP_ID, "http://localhost:8080/api/payments/yookassa", "Отправить тестовое письмо");

        post("/admin/settings/yookassa", "/admin/settings", ADMIN);
        assertThat(get("/admin/settings", ADMIN).body()).contains("Ключи работают: магазин " + FakeYooKassa.SHOP_ID + ", тестовый");

        post("/admin/settings/test-mail", "/admin/settings", ADMIN, "email", "owner@example.ru");
        assertThat(get("/admin/settings", ADMIN).body()).contains("Письмо отправлено на owner@example.ru");
        assertThat(mailbox.to("owner@example.ru")).singleElement().satisfies(l -> assertThat(l.subject()).isEqualTo("Проверка почты магазина"));

        mailbox.setDown(true);
        try {
            post("/admin/settings/test-mail", "/admin/settings", ADMIN, "email", "owner@example.ru");
            // Админу видна настоящая причина, а не «попробуйте позже»
            assertThat(get("/admin/settings", ADMIN).body()).contains("Письмо не ушло: Почтовый сервер недоступен");
        } finally {
            mailbox.setDown(false);
        }
    }

    // ---------- помощники ----------

    private Order newOrder() {
        var cpu = catalog.product("intel-core-i5-12400f-oem");
        return orders.create(Channel.SITE, new CustomerRequest("Покупатель", "+7 900 000-00-00", "Москва", null),
                List.of(new OrderLineRequest(cpu.getId(), 1)), null);
    }

    /** Нажимает «Оплатить» и возвращает id созданного платежа. */
    private String pay(Order order) throws Exception {
        assertThat(startPayment(order).statusCode()).isEqualTo(302);
        return orders.byPublicId(order.getPublicId()).getPaymentId();
    }

    private HttpResponse<String> startPayment(Order order) throws Exception {
        return post("/pay/" + order.getPublicId() + "/online", "/pay/" + order.getPublicId(), null);
    }

    private HttpResponse<String> webhook(String event, String paymentId) throws Exception {
        String json = "{\"type\":\"notification\",\"event\":\"" + event + "\",\"object\":{\"id\":\"" + paymentId
                + "\",\"status\":\"succeeded\",\"paid\":true}}";
        return send(HttpRequest.newBuilder(uri("/api/payments/yookassa")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    /** Возвраты по платежу. Запросы с одним Idempotence-Key — один возврат, как у настоящей ЮKassa. */
    private static List<FakeYooKassa.Call> refunds(String paymentId) {
        var byKey = new java.util.LinkedHashMap<String, FakeYooKassa.Call>();
        YOOKASSA.calls("POST", "/refunds").stream().filter(c -> paymentId.equals(c.body().get("payment_id")))
                .forEach(c -> byKey.putIfAbsent(c.idempotenceKey(), c));
        return List.copyOf(byKey.values());
    }

    @SuppressWarnings("unchecked")
    private static String metadata(Map<String, Object> body) {
        return String.valueOf(((Map<String, Object>) body.get("metadata")).get("order"));
    }

    /** Форма с CSRF-токеном из скрытого поля на странице pageWithToken. */
    private HttpResponse<String> post(String path, String pageWithToken, String auth, String... pairs) throws Exception {
        var body = new StringBuilder("_csrf=").append(enc(csrf(get(pageWithToken, auth).body())));
        for (int i = 0; i < pairs.length; i += 2) {
            body.append('&').append(pairs[i]).append('=').append(enc(pairs[i + 1]));
        }
        var b = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (auth != null) {
            b.header("Authorization", auth);
        }
        return send(b);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return get(path, null);
    }

    private HttpResponse<String> get(String path, String auth) throws Exception {
        var b = HttpRequest.newBuilder(uri(path));
        if (auth != null) {
            b.header("Authorization", auth);
        }
        return send(b);
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String csrf(String html) {
        var m = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(html);
        assertThat(m.find()).as("CSRF-токен в форме").isTrue();
        return m.group(1);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
