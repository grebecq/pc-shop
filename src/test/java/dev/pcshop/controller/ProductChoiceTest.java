package dev.pcshop.controller;

import dev.pcshop.TestcontainersConfig;
import dev.pcshop.dto.request.CustomerRequest;
import dev.pcshop.dto.request.OrderLineRequest;
import dev.pcshop.enums.Channel;
import dev.pcshop.enums.DeliveryMethod;
import dev.pcshop.repository.AccountRepository;
import dev.pcshop.service.CatalogService;
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
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Выбор товара: отзывы покупателей, избранное, сравнение, подсказки в поиске, похожие и недавние товары. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class ProductChoiceTest {

    @LocalServerPort int port;
    @Autowired CatalogService catalog;
    @Autowired OrderService orders;
    @Autowired AccountRepository accounts;

    /** Отдельный «браузер» со своими cookie. */
    class Browser {
        final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

        HttpResponse<String> get(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(uri(path)).build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> post(String path, String pageWithToken, String... pairs) throws Exception {
            var body = new StringBuilder("_csrf=").append(enc(find(get(pageWithToken).body(), "name=\"csrf-token\" content=\"([^\"]+)\"")));
            for (int i = 0; i < pairs.length; i += 2) {
                body.append('&').append(pairs[i]).append('=').append(enc(pairs[i + 1]));
            }
            return http.send(HttpRequest.newBuilder(uri(path))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
        }

        long register() throws Exception {
            String email = "choice-" + UUID.randomUUID() + "@example.com";
            var r = post("/register", "/register", "name", "Мария", "email", email, "password", "secret-123", "consent", "true");
            assertThat(r.statusCode()).isEqualTo(302);
            return accounts.findByEmail(email).orElseThrow().getId();
        }
    }

    @Test
    void onlyBuyerCanReviewAndRatingIsRecalculated() throws Exception {
        var product = catalog.product("asrock-b650m-pro-x3d");
        String page = "/p/" + product.getSlug();

        var guest = new Browser();
        assertThat(guest.get(page).body()).contains("Отзывов пока нет", "Отзывы пишут покупатели этого товара");
        assertThat(guest.post(page + "/reviews", page, "rating", "5", "text", "Хорошая плата").statusCode())
                .as("гостя отправляем войти").isEqualTo(302);

        var maria = new Browser();
        long accountId = maria.register();
        assertThat(maria.get(page).body()).contains("Отзыв можно оставить после покупки").doesNotContain("Ваш отзыв");
        maria.post(page + "/reviews", page, "rating", "5", "text", "Хорошая плата, всё работает");
        assertThat(maria.get(page).body()).contains("Отзыв можно оставить после покупки товара");

        // Купила и оплатила — теперь можно
        var order = orders.create(Channel.SITE, new CustomerRequest("Мария", "+7 900 000-00-00", "Москва, ул. Ленина, 1", null),
                DeliveryMethod.PICKUP, null, List.of(new OrderLineRequest(product.getId(), 1)), null, accountId);
        orders.markPaid(order.getPublicId());
        assertThat(maria.get(page).body()).contains("Ваш отзыв");

        var tooShort = maria.post(page + "/reviews", page, "rating", "4", "text", "Ок");
        assertThat(tooShort.statusCode()).isEqualTo(302);
        assertThat(maria.get(page).body()).contains("Комментарий — от 10 до 2000 символов");

        maria.post(page + "/reviews", page, "rating", "4", "pros", "Много портов", "text", "Собрала на ней ПК, всё стабильно");
        var after = maria.get(page).body();
        assertThat(after).contains("Спасибо! Отзыв опубликован", "Много портов", "Купил в «Сборке»", "Мария",
                "Спасибо, ваш отзыв об этом товаре уже опубликован");
        var rated = catalog.product(product.getSlug());
        assertThat(rated.getReviewsCount()).isEqualTo(1);
        assertThat(rated.getRating()).isEqualByComparingTo("4.0");

        maria.post(page + "/reviews", page, "rating", "1", "text", "Второй отзыв на тот же товар");
        assertThat(maria.get(page).body()).contains("Вы уже оставили отзыв об этом товаре");
        assertThat(catalog.product(product.getSlug()).getReviewsCount()).isEqualTo(1);
    }

    @Test
    void favoritesAreKeptInAccount() throws Exception {
        long ssd = catalog.product("samsung-990-pro-1-tb-m-2-nvme").getId();
        var guest = new Browser();
        assertThat(guest.post("/account/favorites/" + ssd, "/").statusCode()).as("гостя отправляем войти").isEqualTo(302);

        var b = new Browser();
        b.register();
        var on = b.post("/account/favorites/" + ssd, "/");
        assertThat(on.statusCode()).isEqualTo(200);
        assertThat(on.body()).isEqualTo("{\"favorite\":true}");

        var favorites = b.get("/account/favorites").body();
        assertThat(favorites).contains("Samsung 990 PRO 1", "data-fav=\"" + ssd + "\"");
        assertThat(b.get("/").body()).as("счётчик в шапке").containsPattern("data-fav-count[^>]*>1<");

        assertThat(b.post("/account/favorites/" + ssd, "/").body()).isEqualTo("{\"favorite\":false}");
        assertThat(b.get("/account/favorites").body()).contains("В избранном пока ничего нет");
    }

    @Test
    void comparisonGroupsByCategoryAndMarksDifferences() throws Exception {
        long gpu1 = catalog.product("asus-dual-geforce-rtx-5070-oc-12-gb").getId();
        long gpu2 = catalog.product("asrock-intel-arc-b580-challenger-oc-12-gb").getId();
        long cpu = catalog.product("amd-ryzen-5-7600-oem").getId();

        var page = new Browser().get("/compare?ids=" + gpu1 + "," + cpu + "," + gpu2).body();
        assertThat(page).contains("Видеокарты", "Процессоры", "Видеопамять, ГБ", "Только различия");
        // Цена у видеокарт разная, а объём памяти одинаковый: 12 ГБ
        assertThat(page).containsPattern("class=\"is-diff\">\\s*<th>Цена</th>")
                .containsPattern("class=\"is-same\">\\s*<th>Видеопамять, ГБ</th>");

        assertThat(new Browser().get("/compare").body()).contains("Здесь пока пусто");
        assertThat(new Browser().get("/compare?ids=abc").statusCode()).isEqualTo(404);
    }

    @Test
    void searchSuggestionsSimilarAndRecentCards() throws Exception {
        var b = new Browser();
        var suggest = b.get("/api/products/suggest?q=" + enc("rtx 5070"));
        assertThat(suggest.statusCode()).isEqualTo(200);
        assertThat(suggest.body()).contains("RTX 5070").contains("\"slug\":");
        assertThat(b.get("/api/products/suggest?q=r").body()).isEqualTo("[]");

        var product = catalog.product("amd-ryzen-5-7600-oem");
        var page = b.get("/p/" + product.getSlug()).body();
        assertThat(page).contains("Похожие товары", "data-viewed=\"" + product.getId() + "\"", "data-recent",
                "★", "из 5");

        long other = catalog.product("intel-core-i5-12400f-oem").getId();
        var cards = b.get("/cards?ids=" + other + "," + product.getId());
        assertThat(cards.statusCode()).isEqualTo(200);
        assertThat(cards.body()).doesNotContain("<html").contains("class=\"card\"", "Intel Core i5-12400F");
        assertThat(cards.body().indexOf("Intel Core i5-12400F")).as("порядок как в запросе")
                .isLessThan(cards.body().indexOf(product.getName()));

        // Сортировка по рейтингу: у Ryzen 5 7600 в демо-отзывах 4,7, у i5-12400F — 4,5, у остальных отзывов нет
        var titles = Pattern.compile("class=\"card__title\"[^>]*>([^<]+)<").matcher(b.get("/c/cpu?sort=rating").body())
                .results().map(m -> m.group(1)).limit(2).toList();
        assertThat(titles).containsExactly(product.getName(), catalog.product("intel-core-i5-12400f-oem").getName());
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
