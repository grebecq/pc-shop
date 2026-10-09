package dev.pcshop.controller;

import dev.pcshop.TestcontainersConfig;
import dev.pcshop.dto.request.CustomerRequest;
import dev.pcshop.dto.request.OrderLineRequest;
import dev.pcshop.entity.Order;
import dev.pcshop.enums.Channel;
import dev.pcshop.enums.OrderStatus;
import dev.pcshop.service.CatalogService;
import dev.pcshop.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Тестовая оплата картой на сайте: успешная оплата, ошибки в форме и отказ банка. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class CardPaymentTest {

    private static final String FUTURE = YearMonth.now().plusYears(2).format(DateTimeFormatter.ofPattern("MM/yy"));

    @LocalServerPort int port;
    @Autowired OrderService orders;
    @Autowired CatalogService catalog;
    @Autowired JdbcClient jdbc;

    private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

    @Test
    void validCardPaysOrderAndKeepsOnlyLastFourDigits() throws Exception {
        Order order = newOrder();

        var response = pay(order, "4242 4242 4242 4242", FUTURE, "123", "IVAN PETROV");
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location").orElseThrow()).endsWith("/order/" + order.getPublicId() + "?paid");

        Order paid = orders.byPublicId(order.getPublicId());
        assertThat(paid.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paid.getPaymentCard()).isEqualTo("Visa •• 4242");
        // В строке заказа в базе только последние 4 цифры, номера целиком нет
        String row = jdbc.sql("SELECT o::text FROM customer_order o WHERE o.id = ?").param(order.getId()).query(String.class).single();
        assertThat(row).contains("Visa •• 4242").doesNotContain("4242424242424242");

        assertThat(get("/order/" + order.getPublicId() + "?paid").body()).contains("Оплата прошла", "Оплачено картой Visa •• 4242");
        // Оплаченный заказ на страницу оплаты больше не пускает
        assertThat(get("/pay/" + order.getPublicId()).statusCode()).isEqualTo(302);
    }

    @Test
    void mirCardIsRecognised() throws Exception {
        Order order = newOrder();
        assertThat(pay(order, "2200 0000 0000 0004", FUTURE, "321", "").statusCode()).isEqualTo(302);
        assertThat(orders.byPublicId(order.getPublicId()).getPaymentCard()).isEqualTo("МИР •• 0004");
    }

    @Test
    void wrongCheckDigitIsShownAndOrderStaysUnpaid() throws Exception {
        Order order = newOrder();

        var response = pay(order, "4242 4242 4242 4241", FUTURE, "987", "IVAN PETROV");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Неверный номер карты");
        assertUnpaid(order);
        // Введённое остаётся в форме, кроме CVC
        assertThat(response.body()).contains("value=\"4242424242424241\"", "value=\"" + FUTURE + "\"", "value=\"IVAN PETROV\"");
        assertThat(cvcInput(response.body())).doesNotContain("value=");
    }

    @Test
    void bankDeclinesTestCard() throws Exception {
        Order order = newOrder();

        var response = pay(order, "4000 0000 0000 0002", FUTURE, "123", "");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Банк отклонил платёж: недостаточно средств", "value=\"4000000000000002\"");
        assertThat(cvcInput(response.body())).doesNotContain("value=");
        assertUnpaid(order);
        assertThat(orders.byPublicId(order.getPublicId()).getPaymentCard()).isNull();
    }

    @Test
    void expiredCardIsRejected() throws Exception {
        Order order = newOrder();

        var response = pay(order, "4242 4242 4242 4242", "01/20", "123", "");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Срок действия карты истёк");
        assertUnpaid(order);
    }

    @Test
    void emptyFormShowsOneMessagePerField() throws Exception {
        Order order = newOrder();

        String body = pay(order, "", "", "", "").body();
        assertThat(body).contains("Введите номер карты", "Укажите срок действия карты", "Введите CVC")
                .doesNotContain("от 16 до 19 цифр", "Неверный номер карты", "в формате ММ/ГГ", "3 цифры");
        assertThat(pay(order, "4242", "13/30", "12", "").body())
                .contains("Номер карты — от 16 до 19 цифр", "в формате ММ/ГГ", "CVC — 3 цифры")
                .doesNotContain("Неверный номер карты", "истёк");
        assertUnpaid(order);
    }

    private void assertUnpaid(Order order) {
        Order fresh = orders.byPublicId(order.getPublicId());
        assertThat(fresh.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        assertThat(fresh.getPaidAt()).isNull();
    }

    private Order newOrder() {
        // Товар с большим остатком, который не трогают другие тесты: заказы здесь резервируют склад
        var cpu = catalog.product("intel-core-i5-12400f-oem");
        return orders.create(Channel.SITE, new CustomerRequest("Покупатель", "+7 900 000-00-00", "Москва", null),
                List.of(new OrderLineRequest(cpu.getId(), 1)), null);
    }

    private HttpResponse<String> pay(Order order, String number, String expiry, String cvc, String holder) throws Exception {
        String path = "/pay/" + order.getPublicId();
        String form = "_csrf=" + enc(csrf(get(path).body())) + "&number=" + enc(number) + "&expiry=" + enc(expiry)
                + "&cvc=" + enc(cvc) + "&holder=" + enc(holder);
        return http.send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(path)).build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String cvcInput(String html) {
        var m = Pattern.compile("<input[^>]*name=\"cvc\"[^>]*>").matcher(html);
        assertThat(m.find()).as("поле CVC в форме").isTrue();
        return m.group();
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
