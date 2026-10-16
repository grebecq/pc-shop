package dev.pcshop.controller;

import dev.pcshop.TestcontainersConfig;
import dev.pcshop.service.StoreInfoService;
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
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class StorePagesTest {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    private static final String ADMIN = "Basic " + Base64.getEncoder().encodeToString("admin:admin-test-pass".getBytes());

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @Autowired StoreInfoService store;

    private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

    @Test
    void sellerDetailsFromAdminAppearOnSite() throws Exception {
        assertThat(get("/offer", null).body()).contains("Публичная оферта", "статья 26.1", "Это демо-магазин: продавец не указан");
        assertThat(get("/admin/settings", ADMIN).body()).contains("Магазин", "8 800 000-00-00", "Перед запуском покажите его юристу");

        var badInn = saveStore("ООО «Сборка»", "12345", "");
        assertThat(badInn.statusCode()).as("форма с ошибкой").isEqualTo(200);
        assertThat(badInn.body()).contains("ИНН — 10 цифр у организации или 12 у ИП и самозанятого");
        assertThat(saveStore("", "7701234567", "").body()).contains("Укажите и продавца, и его ИНН");
        assertThat(saveStore("ООО «Сборка»", "7701234567", "123").body()).contains("ОГРН — 13 цифр, ОГРНИП — 15");

        try {
            var saved = saveStore("ООО «Сборка»", "7701234567", "1027700000001");
            assertThat(saved.statusCode()).isEqualTo(302);
            assertThat(get("/admin/settings", ADMIN).body()).contains("Настройки магазина сохранены");

            String seller = "ООО «Сборка», ИНН 7701234567, ОГРН 1027700000001, 101000, Москва, ул. Мясницкая, 1";
            assertThat(get("/", null).body()).contains(seller, "8 495 000-00-00", "href=\"tel:+74950000000\"", "href=\"/offer\"");
            assertThat(get("/contacts", null).body()).contains("8 495 000-00-00", "shop@example.ru", "с 10 до 20", seller)
                    .doesNotContain("Это демо-магазин");
            assertThat(get("/offer", null).body()).contains("ООО «Сборка» (далее — продавец)", "ОГРН",
                    "1027700000001", "mailto:shop@example.ru").doesNotContain("Это демо-магазин");
            assertThat(get("/privacy", null).body()).contains("Оператор персональных данных — " + seller);
            assertThat(get("/warranty", null).body()).contains("публичной оферте").doesNotContain("Это демо-магазин");
        } finally {
            jdbc.sql("""
                    UPDATE store_info SET phone = '8 800 000-00-00', email = 'hello@pc-shop.local',
                        hours = 'ежедневно 9:00–21:00, звонок бесплатный', legal_name = NULL, inn = NULL, ogrn = NULL, legal_address = NULL""")
                    .update();
        }
    }

    @Test
    void brandFromAdminAppearsOnSite() throws Exception {
        assertThat(saveStore("Мегабайт", "ноутбуки и техника", "red", "", "", "").body()).contains("Цвет — в виде #2b50d9");
        assertThat(saveStore("", "ноутбуки и техника", "#ff6600", "", "", "").body()).contains("Укажите название магазина");

        try {
            assertThat(saveStore("Мегабайт", "ноутбуки и техника", "#FF6600", "", "", "").statusCode()).isEqualTo(302);
            String home = get("/", null).body();
            assertThat(home).contains("<title>Комплектующие для ПК · Мегабайт</title>",
                    "<meta property=\"og:site_name\" content=\"Мегабайт\">",
                    ":root { --accent: #ff6600; --accent-ink: #ffffff; }",
                    "<span>Мегабайт</span>", "«Мегабайт» — ноутбуки и техника", "class=\"logo__mark\"")
                    .doesNotContain("logo__img", "«Сборка»");
            assertThat(get("/c/cpu", null).body()).contains("content=\"Процессоры в магазине «Мегабайт»");
            assertThat(get("/admin/settings", ADMIN).body()).contains("Мегабайт · админка", "value=\"#ff6600\"");

            store.saveLogo(PNG);
            home = get("/", null).body();
            assertThat(home).containsPattern("<img class=\"logo__img\" src=\"/media/logo\\?v=\\d+\"")
                    .containsPattern("<link rel=\"icon\" href=\"/media/logo\\?v=\\d+\">")
                    .doesNotContain("class=\"logo__mark\"");
            var logo = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/media/logo")).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertThat(logo.headers().firstValue("Content-Type")).hasValue("image/png");
            assertThat(logo.body()).isEqualTo(PNG);

            assertThatThrownBy(() -> store.saveLogo("<svg/>".getBytes())).hasMessageContaining("PNG, JPEG или WebP");
            store.deleteLogo();
            assertThat(get("/", null).body()).contains("class=\"logo__mark\"").doesNotContain("logo__img");
            assertThat(get("/media/logo", null).statusCode()).isEqualTo(404);
        } finally {
            store.deleteLogo();
            jdbc.sql("UPDATE store_info SET name = 'Сборка', tagline = 'комплектующие и сборка ПК', accent = '#2b50d9'").update();
        }
    }

    @Test
    void cartLinksToOffer() throws Exception {
        assertThat(get("/cart", null).body()).contains("Оформляя заказ, вы принимаете условия", "href=\"/offer\"");
    }

    @Test
    void robotsAndSitemapForSearchEngines() throws Exception {
        var robots = get("/robots.txt", null);
        assertThat(robots.headers().firstValue("Content-Type").orElseThrow()).startsWith("text/plain");
        assertThat(robots.body()).contains("User-agent: *", "Disallow: /admin", "Disallow: /account", "Disallow: /cart",
                "Sitemap: http://localhost:8080/sitemap.xml");

        var sitemap = get("/sitemap.xml", null);
        assertThat(sitemap.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/xml");
        String hidden = jdbc.sql("SELECT slug FROM product WHERE NOT active LIMIT 1").query(String.class).single();
        assertThat(sitemap.body()).startsWith("<?xml")
                .contains("<loc>http://localhost:8080/</loc>", "<loc>http://localhost:8080/c/cpu</loc>",
                        "<loc>http://localhost:8080/offer</loc>",
                        "<loc>http://localhost:8080/p/kingston-fury-beast-white-32-gb-2x16-ddr5-6000-cl36</loc><lastmod>")
                .doesNotContain("/p/" + hidden + "<");
        long active = jdbc.sql("SELECT count(*) FROM product WHERE active").query(Long.class).single();
        assertThat(Pattern.compile("<loc>[^<]+/p/").matcher(sitemap.body()).results().count()).isEqualTo(active);
    }

    @Test
    void pagesDescribeThemselvesForSearchAndMessengers() throws Exception {
        String product = get("/p/kingston-fury-beast-white-32-gb-2x16-ddr5-6000-cl36?from=tg", null).body();
        assertThat(product).contains(
                "<link rel=\"canonical\" href=\"http://localhost:8080/p/kingston-fury-beast-white-32-gb-2x16-ddr5-6000-cl36\">",
                "<meta property=\"og:title\" content=\"Kingston FURY Beast White",
                "<meta property=\"og:image\" content=\"http");
        assertThat(find(product, "<meta name=\"description\" content=\"([^\"]+)\"")).contains("Цена 67 990 ₽");

        String category = get("/c/cpu?socket=AM5", null).body();
        assertThat(category).contains("<link rel=\"canonical\" href=\"http://localhost:8080/c/cpu\">",
                "content=\"Процессоры в магазине «Сборка»");
        assertThat(get("/", null).body()).contains("<meta name=\"description\" content=\"Комплектующие для ПК с проверкой совместимости")
                .doesNotContain("Spring Boot", "consent.js");
    }

    private HttpResponse<String> saveStore(String legalName, String inn, String ogrn) throws Exception {
        return saveStore("Сборка", "комплектующие и сборка ПК", "#2b50d9", legalName, inn, ogrn);
    }

    private HttpResponse<String> saveStore(String name, String tagline, String accent,
                                           String legalName, String inn, String ogrn) throws Exception {
        return post("/admin/settings/store", "/admin/settings",
                "name", name, "tagline", tagline, "accent", accent, "phone", "8 495 000-00-00", "email", "shop@example.ru", "hours", "с 10 до 20, кроме воскресенья",
                "legalName", legalName, "inn", inn, "ogrn", ogrn, "legalAddress", "101000, Москва, ул. Мясницкая, 1");
    }

    private HttpResponse<String> get(String path, String auth) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (auth != null) {
            b.header("Authorization", auth);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String pageWithForm, String... pairs) throws Exception {
        var body = new StringBuilder("_csrf=").append(enc(find(get(pageWithForm, ADMIN).body(), "name=\"_csrf\" value=\"([^\"]+)\"")));
        for (int i = 0; i < pairs.length; i += 2) {
            body.append('&').append(enc(pairs[i])).append('=').append(enc(pairs[i + 1]));
        }
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", ADMIN).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
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
