package dev.pcshop.controller;

import dev.pcshop.TestcontainersConfig;
import dev.pcshop.service.CatalogService;
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
import java.util.Base64;
import java.util.Collections;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Аккаунт покупателя: регистрация, адреса, заказ с сохранённым адресом, вход и чужие данные. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class AccountFlowTest {

    @LocalServerPort int port;
    @Autowired CatalogService catalog;

    /** Отдельный «браузер» со своими cookie. */
    class Browser {
        final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

        HttpResponse<String> get(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(uri(path)).build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> postForm(String path, String pageWithForm, String... pairs) throws Exception {
            var body = new StringBuilder("_csrf=").append(enc(formCsrf(get(pageWithForm).body())));
            for (int i = 0; i < pairs.length; i += 2) {
                body.append('&').append(pairs[i]).append('=').append(enc(pairs[i + 1]));
            }
            return http.send(HttpRequest.newBuilder(uri(path))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> postJson(String path, String json, boolean withCsrf) throws Exception {
            var b = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json));
            if (withCsrf) {
                String cart = get("/cart").body();
                b.header(find(cart, "name=\"csrf-header\" content=\"([^\"]+)\""), metaCsrf(cart));
            }
            return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        }

        String register(String email) throws Exception {
            var r = postForm("/register", "/register", "name", "Анна", "email", email, "password", "secret-123");
            assertThat(r.statusCode()).isEqualTo(302);
            return r.headers().firstValue("Location").orElseThrow();
        }
    }

    @Test
    void registerSaveAddressAndOrderWithIt() throws Exception {
        var anna = new Browser();
        String email = "anna-" + UUID.randomUUID() + "@example.com";
        assertThat(anna.register(email)).endsWith("/account?welcome");
        assertThat(anna.get("/account").body()).contains("Заказов пока нет", email);

        var added = anna.postForm("/account/addresses", "/account/addresses",
                "label", "Дом", "city", "Казань", "street", "ул. Баумана, 10", "apartment", "5", "postalCode", "420111");
        assertThat(added.statusCode()).isEqualTo(302);
        var addresses = anna.get("/account/addresses").body();
        assertThat(addresses).contains("Казань, ул. Баумана, 10, кв. 5, индекс 420111", "Подставляется в заказ");
        long addressId = Long.parseLong(find(addresses, "/account/addresses/(\\d+)\""));

        // Корзина подставляет адрес, заказ через API с сохранённым адресом
        assertThat(anna.get("/cart").body()).contains("ул. Баумана, 10");
        long ssd = catalog.product("kingston-nv3-1-tb-m-2-nvme").getId();
        String order = """
                {"customer": {"name": "Анна", "phone": "+7 900 111-22-33", "address": "подменённый адрес"},
                 "items": [{"productId": %d, "quantity": 1}], "addressId": %d}""".formatted(ssd, addressId);
        assertThat(anna.postJson("/api/orders", order, false).statusCode()).as("без CSRF-токена").isEqualTo(403);
        var created = anna.postJson("/api/orders", order, true);
        assertThat(created.statusCode()).isEqualTo(201);
        String publicId = find(created.body(), "\"publicId\":\"([^\"]+)\"");

        // Адрес взят из базы, а не из текста запроса; заказ виден в кабинете
        assertThat(anna.get("/order/" + publicId).body()).contains("Казань, ул. Баумана").doesNotContain("подменённый");
        assertThat(anna.get("/account").body()).contains("Ждёт оплаты");
        assertThat(anna.get("/account/profile").body()).as("телефон из заказа сохранён").contains("+7 900 111-22-33");

        // Другой покупатель не видит и не может использовать чужой адрес
        var boris = new Browser();
        boris.register("boris-" + UUID.randomUUID() + "@example.com");
        assertThat(boris.get("/account/addresses/" + addressId).statusCode()).isEqualTo(404);
        assertThat(boris.postJson("/api/orders", order, true).statusCode()).isEqualTo(400);
    }

    @Test
    void apiExplainsInvalidRequests() throws Exception {
        var b = new Browser();
        long ssd = catalog.product("kingston-nv3-1-tb-m-2-nvme").getId();

        var noCustomer = b.postJson("/api/orders", """
                {"items": [{"productId": %d, "quantity": 1}]}""".formatted(ssd), true);
        assertThat(noCustomer.statusCode()).isEqualTo(400);
        assertThat(noCustomer.body()).contains("\"message\":\"Укажите данные покупателя\"");

        var zeroQty = b.postJson("/api/orders", """
                {"customer": {"name": "Иван", "phone": "+7 900 000-00-00", "address": "Москва"},
                 "items": [{"productId": %d, "quantity": 0}]}""".formatted(ssd), true);
        assertThat(zeroQty.statusCode()).isEqualTo(400);
        assertThat(zeroQty.body()).contains("Количество — от 1 до 20");

        assertThat(b.postJson("/api/orders", "{not json", true).body()).contains("Некорректное тело запроса");

        String tooMany = String.join(",", Collections.nCopies(51, "1"));
        assertThat(b.get("/api/products/by-ids?ids=" + tooMany).statusCode()).isEqualTo(400);
        var missing = b.get("/api/products/no-such-product");
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(missing.body()).contains("\"error\":\"Not found\"");
    }

    @Test
    void newAddressFromCheckoutIsSaved() throws Exception {
        var user = new Browser();
        user.register("new-" + UUID.randomUUID() + "@example.com");
        long ram = catalog.product("kingston-fury-beast-32-gb-2x16-ddr5-6000-cl36").getId();
        var created = user.postJson("/api/orders", """
                {"customer": {"name": "Иван", "phone": "+7 900 000-00-00", "address": "Самара, ул. Ленина, 1"},
                 "items": [{"productId": %d, "quantity": 1}],
                 "newAddress": {"label": "ул. Ленина, 1", "city": "Самара", "street": "ул. Ленина, 1", "postalCode": "", "makeDefault": false},
                 "saveAddress": true}""".formatted(ram), true);
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(user.get("/account/addresses").body()).contains("Самара, ул. Ленина, 1", "Подставляется в заказ");
    }

    @Test
    void loginRedirectsSafely() throws Exception {
        var b = new Browser();
        String email = "login-" + UUID.randomUUID() + "@example.com";
        b.register(email);
        b.postForm("/logout", "/account");

        assertThat(b.get("/account").statusCode()).as("кабинет без входа").isEqualTo(302);
        var wrong = b.postForm("/login", "/login", "email", email, "password", "wrong-pass");
        assertThat(wrong.headers().firstValue("Location").orElseThrow()).endsWith("/login?error");

        var toCart = b.postForm("/login", "/login", "email", email.toUpperCase(), "password", "secret-123", "next", "/cart");
        assertThat(toCart.headers().firstValue("Location").orElseThrow()).endsWith("/cart");

        var other = new Browser();
        other.register("x-" + UUID.randomUUID() + "@example.com");
        other.postForm("/logout", "/account");
        var evil = other.postForm("/login", "/login", "email", email, "password", "secret-123", "next", "//evil.example");
        assertThat(evil.headers().firstValue("Location").orElseThrow()).doesNotContain("evil");
    }

    @Test
    void anonymousVisitorGetsNoSession() throws Exception {
        var home = new Browser().get("/");
        assertThat(home.headers().allValues("Set-Cookie")).noneMatch(c -> c.startsWith("JSESSIONID"));
    }

    @Test
    void tooManyWrongPasswordsLockLogin() throws Exception {
        var b = new Browser();
        String email = "brute-" + UUID.randomUUID() + "@example.com";
        b.register(email);
        b.postForm("/logout", "/account");
        for (int i = 0; i < 10; i++) {
            b.postForm("/login", "/login", "email", email, "password", "wrong-" + i);
        }
        var correct = b.postForm("/login", "/login", "email", email, "password", "secret-123");
        assertThat(correct.headers().firstValue("Location").orElseThrow()).endsWith("/login?locked");
    }

    @Test
    void brokenLinksGive404() throws Exception {
        assertThat(new Browser().get("/order/not-a-uuid").statusCode()).isEqualTo(404);
        assertThat(new Browser().get("/order/" + UUID.randomUUID()).statusCode()).isEqualTo(404);
    }

    @Test
    void searchFindsByEveryWord() throws Exception {
        var b = new Browser();
        assertThat(b.get("/search?q=" + enc("rtx 5070 asus")).body()).contains("ASUS Dual GeForce RTX 5070").doesNotContain("Palit");
        assertThat(b.get("/search?q=" + enc("100%")).body()).contains("Ничего не нашлось");
    }

    @Test
    void adminUploadsProductPhoto() throws Exception {
        String auth = "Basic " + Base64.getEncoder().encodeToString("admin:admin".getBytes());
        var http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        var page = http.send(HttpRequest.newBuilder(uri("/admin/products")).header("Authorization", auth).build(),
                HttpResponse.BodyHandlers.ofString()).body();
        long id = catalog.product("deepcool-cc560-v2-black").getId();

        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        String boundary = "----b" + UUID.randomUUID();
        var body = multipart(boundary, formCsrf(page), "photo.png", png);
        var upload = http.send(HttpRequest.newBuilder(uri("/admin/products/" + id + "/photo"))
                .header("Authorization", auth).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(upload.statusCode()).isEqualTo(302);

        var photo = http.send(HttpRequest.newBuilder(uri("/media/products/" + id)).build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(photo.statusCode()).isEqualTo(200);
        assertThat(photo.headers().firstValue("Content-Type").orElseThrow()).isEqualTo("image/png");
        assertThat(new Browser().get("/p/deepcool-cc560-v2-black").body()).contains("/media/products/" + id + "?v=");

        // Не картинка (HTML под видом PNG) не принимается
        var fake = multipart(boundary, formCsrf(page), "x.png", "<script>alert(1)</script>".getBytes());
        http.send(HttpRequest.newBuilder(uri("/admin/products/" + id + "/photo"))
                .header("Authorization", auth).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(fake)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(http.send(HttpRequest.newBuilder(uri("/media/products/" + id)).build(), HttpResponse.BodyHandlers.ofByteArray())
                .headers().firstValue("Content-Type").orElseThrow()).isEqualTo("image/png");
    }

    private static byte[] multipart(String boundary, String csrf, String filename, byte[] file) {
        var out = new java.io.ByteArrayOutputStream();
        out.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"_csrf\"\r\n\r\n" + csrf + "\r\n"
                + "--" + boundary + "\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.writeBytes(file);
        out.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String formCsrf(String html) {
        return find(html, "name=\"_csrf\" value=\"([^\"]+)\"");
    }

    private static String metaCsrf(String html) {
        return find(html, "name=\"csrf-token\" content=\"([^\"]+)\"");
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
