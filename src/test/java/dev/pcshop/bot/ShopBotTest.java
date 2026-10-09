package dev.pcshop.bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pcshop.TestcontainersConfig;
import dev.pcshop.catalog.CatalogService;
import dev.pcshop.config.ShopProperties;
import dev.pcshop.order.Channel;
import dev.pcshop.order.Order;
import dev.pcshop.order.OrderService;
import dev.pcshop.order.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.Invocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

/** Полный сценарий покупки в боте: каталог → товар → корзина → оформление → тестовая оплата. */
@SpringBootTest
@Import(TestcontainersConfig.class)
class ShopBotTest {

    private static final long CHAT = 777L;
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired CatalogService catalog;
    @Autowired OrderService orders;
    @Autowired BotCart cart;
    @Autowired dev.pcshop.build.BuildService builds;

    TelegramClient telegram;
    ShopBot bot;
    int updateId;

    @BeforeEach
    void setUp() {
        telegram = mock(TelegramClient.class);
        var props = new ShopProperties("http://localhost:8080", null, new ShopProperties.Telegram("test", null, null, null));
        bot = new ShopBot(telegram, catalog, orders, cart, builds, props, java.time.Duration.ofMinutes(30));
        cart.clear(CHAT);
    }

    @Test
    void fullPurchase() throws Exception {
        long ram = catalog.product("kingston-fury-beast-16-gb-2x8-ddr5-5600-cl40").getId();

        text("/start");
        assertThat(lastText()).contains("магазин комплектующих");

        callback("c:ram:0");
        assertThat(lastText()).contains("Оперативная память");
        assertThat(lastButtons()).contains("p:" + ram);

        callback("p:" + ram);
        assertThat(lastText()).contains("Kingston FURY Beast").contains("Тип памяти: DDR5");

        callback("a:" + ram);
        callback("a:" + ram);
        callback("cart");
        assertThat(lastText()).contains("2 × 32 990 ₽").contains("Итого: 65 980 ₽");

        callback("co");
        text("Иван");
        contact("79001234567");
        text("Москва, ул. Тверская, 1");

        assertThat(sentTexts()).anyMatch(t -> t.contains("создан") && t.contains("65 980 ₽"));
        assertThat(cart.lines(CHAT)).isEmpty();

        String payData = lastButtons().stream().filter(d -> d.startsWith("pay:")).findFirst().orElseThrow();
        UUID publicId = UUID.fromString(payData.substring(4));
        Order order = orders.byPublicId(publicId);
        assertThat(order.getChannel()).isEqualTo(Channel.TELEGRAM);
        assertThat(order.getPhone()).isEqualTo("+79001234567");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);

        callback(payData);
        assertThat(orders.byPublicId(publicId).getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(lastText()).contains("оплачен");
    }

    @Test
    void cancelStopsCheckout() throws Exception {
        callback("a:" + catalog.product("kingston-nv3-1-tb-m-2-nvme").getId());
        callback("co");
        text("Отменить оформление");
        text("Иван");
        assertThat(lastText()).contains("Не понял команду");
    }

    @Test
    void staleOrFakeButtonsAreAnswered() throws Exception {
        callback("a:999999");
        callback("p:999999");
        callback("c:nope:0");
        callback("garbage");
        // Каждое нажатие получило ответ (иначе у кнопки вечно крутятся часики), в корзину ничего не попало
        assertThat(toasts()).containsExactly("Товара нет в наличии", "Кнопка устарела", "Кнопка устарела", "Кнопка устарела");
        assertThat(cart.lines(CHAT)).isEmpty();
    }

    @Test
    void payButtonForCancelledOrderDoesNotSayPaid() throws Exception {
        callback("a:" + catalog.product("deepcool-cc560-v2-black").getId());
        callback("co");
        text("Иван");
        text("+7 900 000-00-00");
        text("Москва, ул. Тверская, 1");
        String payData = lastButtons().stream().filter(d -> d.startsWith("pay:")).findFirst().orElseThrow();
        assertThat(lastText()).contains("зарезервирован до");

        orders.changeStatus(orders.byPublicId(UUID.fromString(payData.substring(4))).getId(), OrderStatus.CANCELLED);
        callback(payData);
        assertThat(lastText()).contains("отменён").doesNotContain("оплачен (");
    }

    @Test
    void buildPcInBot() throws Exception {
        builds.saveBotBuild(CHAT, dev.pcshop.build.Build.empty());
        text(ShopBot.BTN_BUILD);
        assertThat(lastText()).contains("Сборка ПК", "Процессор: —");
        assertThat(lastButtons()).contains("bs:cpu:0", "bpl");

        // Готовая сборка «Игровой 2K» совместима
        callback("bpl");
        assertThat(lastText()).contains("Готовые сборки", "Игровой 2K");
        callback("bpre:2");
        assertThat(lastText()).contains("готова к заказу", "AMD Ryzen 7 7800X3D").doesNotContain("❌");

        // В списке плат — только платы под AM5, выбранная отмечена
        callback("bs:mb:0");
        long am4Board = catalog.product("msi-mpg-b550-gaming-plus").getId();
        long am5Board = catalog.product("gigabyte-a620m-gaming-x").getId();
        assertThat(lastButtons()).contains("bp:mb:" + am5Board, "br:mb", "b").doesNotContain("bp:mb:" + am4Board);

        callback("bp:mb:" + am5Board);
        assertThat(lastText()).contains("Gigabyte A620M GAMING X");

        // Кнопка с товаром не той категории или подделанная — не ломает сборку
        long ssd = catalog.product("kingston-nv3-1-tb-m-2-nvme").getId();
        callback("bp:mb:" + ssd);
        callback("bs:zzz:0");
        callback("bpre:99");
        assertThat(toasts()).containsSequence("Кнопка устарела", "Кнопка устарела", "Кнопка устарела");
        assertThat(builds.botBuild(CHAT).part(dev.pcshop.build.Slot.MOTHERBOARD).getId()).isEqualTo(am5Board);

        callback("br:gpu");
        assertThat(lastText()).contains("Видеокарта: —");

        callback("bcart");
        assertThat(toasts().getLast()).isEqualTo("Сборка в корзине");
        assertThat(cart.lines(CHAT)).hasSize(7);
        assertThat(lastText()).contains("Корзина");

        callback("bclr");
        assertThat(builds.botBuild(CHAT).isEmpty()).isTrue();
    }

    // ---------- Помощники: собираем Update из JSON, как его присылает Telegram ----------

    private void text(String text) throws Exception {
        message("\"text\": " + JSON.writeValueAsString(text));
    }

    private void contact(String phone) throws Exception {
        message("\"contact\": {\"phone_number\": \"" + phone + "\", \"first_name\": \"Иван\"}");
    }

    private void message(String body) throws Exception {
        bot.consume(update("""
                {"update_id": %d, "message": {"message_id": %d, "date": 1700000000,
                 "chat": {"id": %d, "type": "private"}, "from": {"id": %d, "is_bot": false, "first_name": "Иван"}, %s}}"""
                .formatted(++updateId, updateId, CHAT, CHAT, body)));
    }

    private void callback(String data) throws Exception {
        bot.consume(update("""
                {"update_id": %d, "callback_query": {"id": "cb%d", "chat_instance": "x", "data": "%s",
                 "from": {"id": %d, "is_bot": false, "first_name": "Иван"},
                 "message": {"message_id": 1, "date": 1700000000, "chat": {"id": %d, "type": "private"}, "text": "..."}}}"""
                .formatted(++updateId, updateId, data, CHAT, CHAT)));
    }

    private static Update update(String json) throws Exception {
        return JSON.readValue(json, Update.class);
    }

    private List<Object> sent() {
        return mockingDetails(telegram).getInvocations().stream()
                .map(Invocation::getArguments).filter(a -> a.length == 1).map(a -> a[0])
                .filter(a -> a instanceof SendMessage || a instanceof EditMessageText)
                .toList();
    }

    private List<String> toasts() {
        return mockingDetails(telegram).getInvocations().stream()
                .map(Invocation::getArguments).filter(a -> a.length == 1).map(a -> a[0])
                .filter(a -> a instanceof org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery)
                .map(a -> ((org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery) a).getText())
                .toList();
    }

    private List<String> sentTexts() {
        return sent().stream().map(m -> m instanceof SendMessage s ? s.getText() : ((EditMessageText) m).getText()).toList();
    }

    private String lastText() {
        return sentTexts().getLast();
    }

    private List<String> lastButtons() {
        Object last = sent().getLast();
        var markup = last instanceof SendMessage s ? s.getReplyMarkup() : ((EditMessageText) last).getReplyMarkup();
        if (!(markup instanceof InlineKeyboardMarkup inline)) {
            return List.of();
        }
        return inline.getKeyboard().stream().flatMap(List::stream)
                .map(b -> b.getCallbackData()).filter(Objects::nonNull).toList();
    }
}
