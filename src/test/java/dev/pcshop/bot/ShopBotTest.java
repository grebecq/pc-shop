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

    TelegramClient telegram;
    ShopBot bot;
    int updateId;

    @BeforeEach
    void setUp() {
        telegram = mock(TelegramClient.class);
        var props = new ShopProperties("http://localhost:8080", null, new ShopProperties.Telegram("test", null, null));
        bot = new ShopBot(telegram, catalog, orders, cart, props);
        cart.clear(CHAT);
    }

    @Test
    void fullPurchase() throws Exception {
        long ram = catalog.product("kingston-fury-beast-32-gb-2x16-ddr5-6000").getId();

        text("/start");
        assertThat(lastText()).contains("демо-магазин");

        callback("c:ram:0");
        assertThat(lastText()).contains("Оперативная память");
        assertThat(lastButtons()).contains("p:" + ram);

        callback("p:" + ram);
        assertThat(lastText()).contains("Kingston FURY Beast").contains("Тип памяти: DDR5");

        callback("a:" + ram);
        callback("a:" + ram);
        callback("cart");
        assertThat(lastText()).contains("2 × 9 490 ₽").contains("Итого: 18 980 ₽");

        callback("co");
        text("Иван");
        contact("79001234567");
        text("Москва, ул. Тверская, 1");

        assertThat(sentTexts()).anyMatch(t -> t.contains("создан") && t.contains("18 980 ₽"));
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
        callback("a:" + catalog.product("kingston-nv3-1-tb").getId());
        callback("co");
        text("Отменить оформление");
        text("Иван");
        assertThat(lastText()).contains("Не понял команду");
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
