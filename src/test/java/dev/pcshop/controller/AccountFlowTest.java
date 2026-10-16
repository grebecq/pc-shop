package dev.pcshop.controller;

import dev.pcshop.TestMailbox;
import dev.pcshop.TestcontainersConfig;
import dev.pcshop.service.CatalogService;
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
import java.util.Base64;
import java.util.Collections;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class AccountFlowTest {

    private static final String PASSWORD_LETTER = "Смена пароля";
    private static final String CONFIRM_LETTER = "Подтвердите почту";

    @LocalServerPort int port;
    @Autowired CatalogService catalog;
    @Autowired TestMailbox mailbox;
    @Autowired JdbcClient jdbc;

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
            var r = postForm("/register", "/register", "name", "Анна", "email", email, "password", "secret-123", "consent", "true");
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

        assertThat(anna.get("/cart").body()).contains("ул. Баумана, 10");
        long ssd = catalog.product("kingston-nv3-1-tb-m-2-nvme").getId();
        String order = """
                {"customer": {"name": "Анна", "phone": "+7 900 111-22-33", "address": "подменённый адрес"},
                 "items": [{"productId": %d, "quantity": 1}], "addressId": %d}""".formatted(ssd, addressId);
        assertThat(anna.postJson("/api/orders", order, false).statusCode()).as("без CSRF-токена").isEqualTo(403);
        var created = anna.postJson("/api/orders", order, true);
        assertThat(created.statusCode()).isEqualTo(201);
        String publicId = find(created.body(), "\"publicId\":\"([^\"]+)\"");

        assertThat(anna.get("/order/" + publicId).body()).contains("Казань, ул. Баумана").doesNotContain("подменённый");
        assertThat(anna.get("/account").body()).contains("Ждёт оплаты");
        assertThat(anna.get("/account/profile").body()).as("телефон из заказа сохранён").contains("+7 900 111-22-33");

        var boris = new Browser();
        boris.register("boris-" + UUID.randomUUID() + "@example.com");
        assertThat(boris.get("/account/addresses/" + addressId).statusCode()).isEqualTo(404);
        var foreign = boris.postJson("/api/orders", order, true);
        assertThat(foreign.statusCode()).isEqualTo(404);
        assertThat(foreign.body()).contains("Адрес не найден");
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
        var badId = b.get("/api/products/by-ids?ids=0");
        assertThat(badId.statusCode()).isEqualTo(400);
        assertThat(badId.body()).contains("id товара — число больше 0");
        var shortAddress = b.postJson("/api/orders", """
                {"customer": {"name": "Иван", "phone": "+7 900 000-00-00", "address": "Мск"},
                 "items": [{"productId": %d, "quantity": 1}]}""".formatted(ssd), true);
        assertThat(shortAddress.body()).contains("Адрес — от 5 до 512 символов");
        var missing = b.get("/api/products/no-such-product");
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(missing.body()).contains("\"error\":\"Not found\"");
    }

    @Test
    void registrationTrimsEmailAndRejectsDuplicates() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@example.com";
        new Browser().register("  " + email.toUpperCase() + " ");

        var again = new Browser();
        var r = again.postForm("/register", "/register", "name", "Анна", "email", email, "password", "secret-123", "consent", "true");
        assertThat(r.statusCode()).as("форма с ошибкой, а не вход").isEqualTo(200);
        assertThat(r.body()).contains("Аккаунт с таким email уже есть");
    }

    @Test
    void forgotPasswordAnswersTheSameForAnyEmail() throws Exception {
        var anna = new Browser();
        String email = "forgot-" + UUID.randomUUID() + "@example.com";
        anna.register(email);
        anna.postForm("/logout", "/account");
        assertThat(anna.get("/login").body()).contains("Забыли пароль?", "/password/forgot");

        var known = anna.postForm("/password/forgot", "/password/forgot", "email", " " + email.toUpperCase() + " ");
        String stranger = "nobody-" + UUID.randomUUID() + "@example.com";
        var unknown = new Browser().postForm("/password/forgot", "/password/forgot", "email", stranger);
        assertThat(known.statusCode()).isEqualTo(302);
        assertThat(unknown.statusCode()).isEqualTo(302);
        assertThat(unknown.headers().firstValue("Location")).as("ответ не выдаёт, есть ли аккаунт")
                .isEqualTo(known.headers().firstValue("Location"));
        assertThat(anna.get("/password/forgot?sent").body())
                .contains("Если такой адрес зарегистрирован, мы отправили на него письмо со ссылкой");

        assertThat(mailbox.to(stranger)).isEmpty();
        var letters = mailbox.to(email, PASSWORD_LETTER);
        assertThat(letters).hasSize(1);
        var letter = letters.getFirst();
        assertThat(letter.from()).isEqualTo("Сборка <noreply@pc-shop.local>");
        assertThat(letter.subject()).contains("Смена пароля");
        assertThat(letter.text()).contains("Здравствуйте, Анна!", "http://localhost:8080/password/reset?token=",
                "действует 30 минут", "Если вы не запрашивали смену пароля, просто проигнорируйте письмо");

        var again = anna.postForm("/password/forgot", "/password/forgot", "email", email);
        assertThat(again.headers().firstValue("Location")).isEqualTo(known.headers().firstValue("Location"));
        assertThat(mailbox.to(email, PASSWORD_LETTER)).hasSize(1);

        var invalid = anna.postForm("/password/forgot", "/password/forgot", "email", "not-an-email");
        assertThat(invalid.statusCode()).as("форма с ошибкой").isEqualTo(200);
        assertThat(invalid.body()).contains("Проверьте email");
    }

    @Test
    void resetLinkChangesPasswordOnlyOnce() throws Exception {
        var b = new Browser();
        String email = "reset-" + UUID.randomUUID() + "@example.com";
        b.register(email);
        b.postForm("/logout", "/account");
        b.postForm("/password/forgot", "/password/forgot", "email", email);
        String link = resetLink(email);
        String token = link.substring(link.indexOf('=') + 1);

        assertThat(b.get(link).body()).contains("Новый пароль", "Повторите пароль").doesNotContain("Ссылка устарела");
        var mismatch = b.postForm("/password/reset", link, "token", token, "password", "new-secret-1", "confirm", "new-secret-2");
        assertThat(mismatch.body()).contains("Пароли не совпадают");
        var tooShort = b.postForm("/password/reset", link, "token", token, "password", "short", "confirm", "short");
        assertThat(tooShort.body()).contains("Пароль — от 8 до 72 символов");

        var changed = b.postForm("/password/reset", link, "token", token, "password", "new-secret-1", "confirm", "new-secret-1");
        assertThat(changed.headers().firstValue("Location").orElseThrow()).endsWith("/login?reset");
        assertThat(b.get("/login?reset").body()).contains("Пароль изменён, войдите с новым паролем");

        assertThat(b.get(link).body()).contains("Ссылка устарела, запросите новую", "/password/forgot");
        var reused = b.postForm("/password/reset", "/login", "token", token, "password", "evil-secret-1", "confirm", "evil-secret-1");
        assertThat(reused.body()).contains("Ссылка устарела, запросите новую");

        var oldPassword = b.postForm("/login", "/login", "email", email, "password", "secret-123", "consent", "true");
        assertThat(oldPassword.headers().firstValue("Location").orElseThrow()).endsWith("/login?error");
        var newPassword = b.postForm("/login", "/login", "email", email, "password", "new-secret-1");
        assertThat(newPassword.headers().firstValue("Location").orElseThrow()).endsWith("/account");
        assertThat(b.get("/account").body()).as("ссылка из письма подтвердила и почту").doesNotContain("Подтвердите почту");
    }

    @Test
    void expiredOrUnknownLinkDoesNotWork() throws Exception {
        var b = new Browser();
        String email = "expired-" + UUID.randomUUID() + "@example.com";
        b.register(email);
        b.postForm("/logout", "/account");
        b.postForm("/password/forgot", "/password/forgot", "email", email);
        String link = resetLink(email);
        jdbc.sql("update password_reset_token set expires_at = now() - interval '1 second' "
                        + "where account_id = (select id from account where email = ?)")
                .param(email).update();

        assertThat(b.get(link).body()).contains("Ссылка устарела, запросите новую");
        var late = b.postForm("/password/reset", "/login", "token", link.substring(link.indexOf('=') + 1),
                "password", "new-secret-1", "confirm", "new-secret-1");
        assertThat(late.body()).contains("Ссылка устарела, запросите новую");
        assertThat(b.get("/password/reset?token=" + "A".repeat(43)).body()).contains("Ссылка устарела");
        assertThat(b.get("/password/reset").body()).contains("Ссылка устарела");

        var login = b.postForm("/login", "/login", "email", email, "password", "secret-123", "consent", "true");
        assertThat(login.headers().firstValue("Location").orElseThrow()).as("пароль прежний").endsWith("/account");
    }

    @Test
    void profileButtonMailsLinkToCurrentUser() throws Exception {
        var b = new Browser();
        String email = "profile-" + UUID.randomUUID() + "@example.com";
        b.register(email);
        assertThat(b.get("/account/profile").body())
                .contains("Отправим на <b>" + email + "</b> ссылку для смены пароля", "Отправить письмо")
                .doesNotContain("Текущий пароль");

        var sent = b.postForm("/account/password", "/account/profile");
        assertThat(sent.headers().firstValue("Location").orElseThrow()).endsWith("/account/profile");
        assertThat(b.get("/account/profile").body()).contains("Письмо отправлено на " + email);
        assertThat(mailbox.to(email, PASSWORD_LETTER)).hasSize(1);

        String link = resetLink(email);
        var changed = b.postForm("/password/reset", link, "token", link.substring(link.indexOf('=') + 1),
                "password", "new-secret-1", "confirm", "new-secret-1");
        assertThat(changed.headers().firstValue("Location").orElseThrow()).endsWith("/login?reset");
        assertThat(b.get("/account").statusCode()).as("кабинет без входа").isEqualTo(302);
    }

    @Test
    void mailServerFailureIsReportedPolitely() throws Exception {
        var b = new Browser();
        String email = "smtp-" + UUID.randomUUID() + "@example.com";
        b.register(email);
        mailbox.setDown(true);
        try {
            b.postForm("/account/password", "/account/profile");
            assertThat(b.get("/account/profile").body()).contains("Не удалось отправить письмо, попробуйте позже");
            var forgot = new Browser().postForm("/password/forgot", "/password/forgot", "email", email);
            assertThat(forgot.statusCode()).isEqualTo(200);
            assertThat(forgot.body()).contains("Не удалось отправить письмо, попробуйте позже");
        } finally {
            mailbox.setDown(false);
        }
        b.postForm("/account/password", "/account/profile");
        assertThat(mailbox.to(email, PASSWORD_LETTER)).hasSize(1);
    }

    @Test
    void registrationAsksToConfirmEmail() throws Exception {
        var b = new Browser();
        String email = "confirm-" + UUID.randomUUID() + "@example.com";
        b.register(email);
        var letters = mailbox.to(email, CONFIRM_LETTER);
        assertThat(letters).hasSize(1);
        assertThat(letters.getFirst().text()).contains("Здравствуйте, Анна!", "http://localhost:8080/email/confirm?token=",
                "действует 3 дня", "Если вы не регистрировались");
        assertThat(b.get("/account").body()).contains("Подтвердите почту <b>" + email + "</b>", "Отправить ещё раз");
        assertThat(b.get("/account/profile").body()).contains("Подтвердите почту");

        b.postForm("/account/email", "/account", "from", "profile");
        assertThat(b.get("/account/profile").body()).contains("Письмо уже отправлено. Новое можно запросить через минуту");
        assertThat(mailbox.to(email, CONFIRM_LETTER)).hasSize(1);

        String link = confirmLink(email);
        var other = new Browser().get(link);
        assertThat(other.body()).contains("Почта подтверждена", "Войти");
        assertThat(other.headers().firstValue("Referrer-Policy")).hasValue("no-referrer");
        assertThat(b.get("/account").body()).doesNotContain("Подтвердите почту");
        assertThat(b.get(link).body()).as("ссылка одноразовая").contains("Ссылка не работает");
        assertThat(b.get("/email/confirm?token=" + "A".repeat(43)).body()).contains("Ссылка не работает");
        assertThat(b.get("/email/confirm").body()).contains("Ссылка не работает");

        b.postForm("/account/email", "/account");
        assertThat(b.get("/account").body()).contains("Почта уже подтверждена");
        assertThat(mailbox.to(email, CONFIRM_LETTER)).hasSize(1);
    }

    @Test
    void oldConfirmLinkExpiresAndNewOneWorks() throws Exception {
        var b = new Browser();
        String email = "late-" + UUID.randomUUID() + "@example.com";
        b.register(email);
        String oldLink = confirmLink(email);
        jdbc.sql("update account set email_token_sent_at = now() - interval '3 days 1 minute' where email = ?").param(email).update();
        assertThat(b.get(oldLink).body()).contains("Ссылка не работает");

        var resent = b.postForm("/account/email", "/account");
        assertThat(resent.headers().firstValue("Location").orElseThrow()).endsWith("/account");
        assertThat(b.get("/account").body()).contains("Письмо отправлено на " + email);
        String newLink = confirmLink(email);
        assertThat(newLink).isNotEqualTo(oldLink);
        assertThat(b.get(oldLink).body()).as("старая ссылка заменена новой").contains("Ссылка не работает");
        assertThat(b.get(newLink).body()).contains("Почта подтверждена", "В личный кабинет");
    }

    @Test
    void registrationWorksWhenMailIsDown() throws Exception {
        var b = new Browser();
        String email = "nomail-" + UUID.randomUUID() + "@example.com";
        mailbox.setDown(true);
        try {
            assertThat(b.register(email)).endsWith("/account?welcome");
        } finally {
            mailbox.setDown(false);
        }
        assertThat(mailbox.to(email)).isEmpty();
        b.postForm("/account/email", "/account");
        assertThat(mailbox.to(email, CONFIRM_LETTER)).hasSize(1);
    }

    @Test
    void swaggerDescribesOnlyApi() throws Exception {
        var docs = new Browser().get("/v3/api-docs");
        assertThat(docs.statusCode()).isEqualTo(200);
        assertThat(docs.body()).contains("/api/orders", "/api/products/by-ids", "Оформить заказ")
                .doesNotContain("\"/account").doesNotContain("\"/admin");
        assertThat(new Browser().get("/swagger-ui/index.html").statusCode()).isEqualTo(200);
    }

    @Test
    void newAddressFromCheckoutIsSaved() throws Exception {
        var user = new Browser();
        user.register("new-" + UUID.randomUUID() + "@example.com");
        long ram = catalog.product("kingston-fury-beast-white-32-gb-2x16-ddr5-6000-cl36").getId();
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
        var correct = b.postForm("/login", "/login", "email", email, "password", "secret-123", "consent", "true");
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
        String auth = "Basic " + Base64.getEncoder().encodeToString("admin:admin-test-pass".getBytes());
        var http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        long id = catalog.product("deepcool-cc560-v2-black").getId();
        var page = http.send(HttpRequest.newBuilder(uri("/admin/products/" + id)).header("Authorization", auth).build(),
                HttpResponse.BodyHandlers.ofString()).body();

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

        var fake = multipart(boundary, formCsrf(page), "x.png", "<script>alert(1)</script>".getBytes());
        http.send(HttpRequest.newBuilder(uri("/admin/products/" + id + "/photo"))
                .header("Authorization", auth).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(fake)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(http.send(HttpRequest.newBuilder(uri("/media/products/" + id)).build(), HttpResponse.BodyHandlers.ofByteArray())
                .headers().firstValue("Content-Type").orElseThrow()).isEqualTo("image/png");

        var missing = http.send(HttpRequest.newBuilder(uri("/admin/products/999999/photo"))
                .header("Authorization", auth).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(boundary, formCsrf(page), "photo.png", png))).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(missing.statusCode()).isEqualTo(404);
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

    private String resetLink(String email) {
        var letters = mailbox.to(email, PASSWORD_LETTER);
        assertThat(letters).as("письмо на " + email).isNotEmpty();
        return find(letters.getLast().text(), "http://localhost:8080(/password/reset\\?token=[A-Za-z0-9_-]{43})\\s");
    }

    private String confirmLink(String email) {
        var letters = mailbox.to(email, CONFIRM_LETTER);
        assertThat(letters).as("письмо на " + email).isNotEmpty();
        return find(letters.getLast().text(), "http://localhost:8080(/email/confirm\\?token=[A-Za-z0-9_-]{43})\\s");
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
