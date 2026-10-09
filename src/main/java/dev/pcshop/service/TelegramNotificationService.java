package dev.pcshop.service;

import dev.pcshop.config.ShopProperties;
import dev.pcshop.entity.Order;
import dev.pcshop.event.OrderEvents;
import dev.pcshop.util.Formats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import static org.springframework.web.util.HtmlUtils.htmlEscape;

/**
 * Уведомления в Telegram: владельцу о новых и оплаченных заказах, покупателю о смене статуса.
 * Срабатывают после коммита и в отдельном потоке, чтобы медленный Telegram не тормозил оформление заказа.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramNotificationService {

    private final ObjectProvider<TelegramClient> telegram;
    private final OrderService orders;
    private final ShopProperties props;

    @Async
    @TransactionalEventListener
    public void onCreated(OrderEvents.Created event) {
        Order order = orders.byId(event.orderId());
        sendToAdmin("🆕 <b>Новый заказ " + order.number() + "</b> · " + order.getChannel().label() + "\n" + describe(order)
                + "\nСтатус: ждёт оплаты");
    }

    @Async
    @TransactionalEventListener
    public void onPaid(OrderEvents.Paid event) {
        Order order = orders.byId(event.orderId());
        sendToAdmin("💰 <b>Заказ " + order.number() + " оплачен</b> · " + Formats.price(order.getTotal()));
    }

    @Async
    @TransactionalEventListener
    public void onStatusChanged(OrderEvents.StatusChanged event) {
        Order order = orders.byId(event.orderId());
        if (order.getTelegramChatId() != null) {
            send(order.getTelegramChatId(), "Заказ " + order.number() + ": " + event.status().label().toLowerCase());
        }
    }

    private String describe(Order order) {
        StringBuilder text = new StringBuilder();
        order.getItems().forEach(i -> text.append("• ").append(htmlEscape(i.getName()))
                .append(" × ").append(i.getQuantity()).append('\n'));
        return text.append("Итого: <b>").append(Formats.price(order.getTotal())).append("</b>\n")
                .append(htmlEscape(order.getCustomerName())).append(", ").append(htmlEscape(order.getPhone())).append('\n')
                .append(htmlEscape(order.getAddress()))
                .toString();
    }

    private void sendToAdmin(String html) {
        Long adminChatId = props.telegram().adminChatId();
        if (adminChatId == null) {
            log.info("ADMIN_CHAT_ID не задан, уведомление не отправлено: {}", html);
            return;
        }
        send(adminChatId, html);
    }

    private void send(long chatId, String html) {
        TelegramClient client = telegram.getIfAvailable();
        if (client == null) {
            log.info("Бот выключен, уведомление для {} пропущено", chatId);
            return;
        }
        try {
            client.execute(SendMessage.builder().chatId(chatId).text(html).parseMode("HTML").build());
        } catch (Exception e) {
            // Частая причина: владелец ещё не нажал /start у бота
            log.warn("Не удалось отправить уведомление в чат {}: {}", chatId, e.getMessage());
        }
    }
}
