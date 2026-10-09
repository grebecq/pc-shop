package dev.pcshop.controller;

import dev.pcshop.TestcontainersConfig;
import dev.pcshop.service.BuildService;
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
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Страницы конфигуратора: сборка из ссылки, выбор детали, сохранение в кабинет. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class ConfiguratorPagesTest {

    @LocalServerPort int port;
    @Autowired BuildService builds;

    private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

    @Test
    void pagesRenderAndExplainProblems() throws Exception {
        assertThat(get("/configurator").body()).contains("Собрать ПК", "Готовые сборки", "Игровой 2K");

        String preset = builds.presets().get(2).build().link();
        var page = get(preset).body();
        assertThat(page).contains("Сборка совместима и готова к заказу", "AMD Ryzen 7 7800X3D", "data-add-build=");

        // Меняем плату на Intel: сборка сразу показывает конфликт сокетов
        long intelBoard = Long.parseLong(find(get("/configurator/mb?" + preset.substring(preset.indexOf('?') + 1)).body(),
                "mb=(\\d+)[^\"]*\"[^>]*>Всё равно"));
        var broken = get(preset.replaceAll("mb=\\d+", "mb=" + intelBoard)).body();
        assertThat(broken).contains("Процессор под сокет AM5").doesNotContain("готова к заказу");

        var gpus = get("/configurator/gpu?" + preset.substring(preset.indexOf('?') + 1)).body();
        assertThat(gpus).contains("Подходят", "Выбрано", "Шаг 5 из 8");

        assertThat(get("/configurator/nope").statusCode()).isEqualTo(404);
        assertThat(get("/configurator?cpu=abc&gpu=-1").statusCode()).isEqualTo(200);
    }

    @Test
    void stepsLeadToNextEmptySlot() throws Exception {
        // Выбор процессора ведёт сразу к платам, а неподходящие платы спрятаны в свёрнутый список
        assertThat(get("/configurator").body()).contains("Начать с процессора", "Сборка ПК");
        String cpuLink = find(get("/configurator/cpu").body(), "href=\"(/configurator/mb\\?cpu=\\d+)\"");
        var boards = get(cpuLink).body();
        assertThat(boards).contains("Шаг 2 из 8", "Не подходят к сборке:", "Пропустить шаг");
        // Пропуск шага ведёт к следующему, на последнем шаге — к итогу сборки
        String cpu = cpuLink.substring(cpuLink.indexOf("cpu="));
        assertThat(boards).contains("href=\"/configurator/cooler?" + cpu + "\"");
        assertThat(get("/configurator/case?" + cpu).body()).contains("href=\"/configurator?" + cpu + "\"");

        // Со страницы товара — сразу в конструктор с этим товаром
        assertThat(get("/p/amd-ryzen-5-7600-oem").body()).containsPattern("class=\"build-with\" href=\"/configurator/mb\\?cpu=\\d+\"")
                .contains("Собрать ПК с этим товаром");
    }

    @Test
    void savingNeedsLoginAndLandsInAccount() throws Exception {
        String preset = builds.presets().get(1).build().link();
        String query = preset.substring(preset.indexOf('?') + 1);

        var anon = get(preset).body();
        assertThat(anon).contains("чтобы сохранить сборку").contains("/login?next=");
        assertThat(post("/configurator/save", preset, query + "&name=x").headers().firstValue("Location").orElseThrow())
                .endsWith("/login");

        // Регистрация со ссылкой «вернуться к сборке»
        var reg = post("/register", "/register", "name=Анна&email=" + enc("b-" + UUID.randomUUID() + "@example.com")
                + "&password=secret-123&next=" + enc(preset));
        assertThat(reg.headers().firstValue("Location").orElseThrow()).endsWith(preset);

        var saved = post("/configurator/save", preset, query + "&name=" + enc("Для брата"));
        assertThat(saved.headers().firstValue("Location").orElseThrow()).endsWith("/account/builds");
        var list = get("/account/builds").body();
        assertThat(list).contains("Для брата", "готова", "Мои сборки");

        String id = find(list, "/account/builds/(\\d+)/delete");
        post("/account/builds/" + id + "/delete", "/account/builds", "");
        assertThat(get("/account/builds").body()).contains("Сохранённых сборок пока нет");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(path)).build(), HttpResponse.BodyHandlers.ofString());
    }

    /** POST формы с CSRF-токеном со страницы pageWithForm. */
    private HttpResponse<String> post(String path, String pageWithForm, String form) throws Exception {
        String csrf = find(get(pageWithForm).body(), "name=\"csrf-token\" content=\"([^\"]+)\"");
        String body = "_csrf=" + enc(csrf) + (form.isEmpty() ? "" : "&" + form);
        return http.send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
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
