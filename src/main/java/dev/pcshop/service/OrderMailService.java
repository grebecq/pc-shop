package dev.pcshop.service;

import dev.pcshop.config.ShopProperties;
import dev.pcshop.entity.Order;
import dev.pcshop.enums.DeliveryMethod;
import dev.pcshop.enums.OrderStatus;
import dev.pcshop.event.OrderEvents;
import dev.pcshop.exception.MailDeliveryException;
import dev.pcshop.util.Formats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;

/**
 * Письма покупателю о заказе: оформлен, оплачен, сменился статус. Уходят, только если в заказе есть email.
 * Как и уведомления в Telegram, срабатывают после коммита и в отдельном потоке: недоступная почта не мешает заказу.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMailService {

    private final MailService mail;
    private final OrderService orders;
    private final ShopProperties props;
    @Value("${shop.orders.payment-timeout:30m}")
    private final Duration paymentTimeout;

    @Async
    @TransactionalEventListener
    public void onCreated(OrderEvents.Created event) {
        Order order = orders.byId(event.orderId());
        if (order.getEmail() == null) {
            return;
        }
        StringBuilder text = greeting(order).append("Заказ ").append(order.number()).append(" оформлен.\n\n");
        appendSummary(text, order);
        text.append("\nОплатить заказ: ").append(link("/pay/" + order.getPublicId()))
                .append("\nТовар зарезервирован до ").append(Formats.timeOfDay(order.payBefore(paymentTimeout)))
                .append(" по Москве. Если не оплатить, заказ отменится.\n");
        send(order, "Заказ " + order.number() + " оформлен", text);
    }

    @Async
    @TransactionalEventListener
    public void onPaid(OrderEvents.Paid event) {
        sendPaid(orders.byId(event.orderId()));
    }

    @Async
    @TransactionalEventListener
    public void onStatusChanged(OrderEvents.StatusChanged event) {
        Order order = orders.byId(event.orderId());
        if (order.getEmail() == null) {
            return;
        }
        if (event.status() == OrderStatus.PAID) {
            // Оплату отметили в админке, а не картой на сайте
            sendPaid(order);
            return;
        }
        String number = order.number();
        String news = switch (event.status()) {
            case ASSEMBLING -> "Собираем заказ " + number + ". Как только он будет готов, напишем.";
            case SHIPPED -> order.getDeliveryMethod() == DeliveryMethod.PICKUP
                    ? "Заказ " + number + " ждёт вас в магазине: " + order.getAddress() + ".\nНазовите на кассе номер заказа."
                    : "Заказ " + number + " передан в доставку. Курьер позвонит перед приездом.";
            case DONE -> "Заказ " + number + " выполнен. Спасибо за покупку!";
            case CANCELLED -> "Заказ " + number + " отменён, товар вернулся на склад."
                    + (order.getPaidAt() != null ? "\nДеньги вернутся на карту в течение нескольких дней." : "");
            case AWAITING_PAYMENT, PAID -> null;
        };
        if (news == null) {
            return;
        }
        send(order, "Заказ " + number + ": " + order.statusLabel().toLowerCase(), greeting(order).append(news).append('\n'));
    }

    private void sendPaid(Order order) {
        if (order.getEmail() == null || order.getStatus() == OrderStatus.CANCELLED) {
            return;
        }
        StringBuilder text = greeting(order).append("Спасибо! Заказ ").append(order.number()).append(" оплачен")
                .append(order.getPaymentCard() != null ? " картой " + order.getPaymentCard() : "").append(".\n")
                .append(order.getDeliveryMethod() == DeliveryMethod.PICKUP
                        ? "Напишем, когда заказ будет ждать вас в магазине.\n\n"
                        : "Напишем, когда передадим его курьеру.\n\n");
        appendSummary(text, order);
        send(order, "Заказ " + order.number() + " оплачен", text);
    }

    private StringBuilder greeting(Order order) {
        return new StringBuilder("Здравствуйте, ").append(order.getCustomerName()).append("!\n\n");
    }

    private void appendSummary(StringBuilder text, Order order) {
        order.getItems().forEach(i -> text.append("• ").append(i.getName()).append(" × ").append(i.getQuantity())
                .append(" — ").append(Formats.price(i.sum())).append('\n'));
        text.append("\nТовары: ").append(Formats.price(order.itemsTotal()));
        if (order.getDiscount() > 0) {
            text.append("\nСкидка по промокоду ").append(order.getPromoCode()).append(": −").append(Formats.price(order.getDiscount()));
        }
        text.append("\nДоставка: ").append(order.getDeliveryPrice() == 0 ? "бесплатно" : Formats.price(order.getDeliveryPrice()))
                .append("\nИтого: ").append(Formats.price(order.getTotal()))
                .append("\n\n").append(order.getDeliveryMethod().label()).append(": ").append(order.getAddress())
                .append("\nСтатус заказа: ").append(link("/order/" + order.getPublicId())).append('\n');
    }

    private String link(String path) {
        return props.baseUrl().replaceAll("/+$", "") + path;
    }

    private void send(Order order, String subject, StringBuilder text) {
        text.append("\nМагазин «Сборка»");
        try {
            mail.send(order.getEmail(), subject, text.toString());
        } catch (MailDeliveryException e) {
            // Причину уже записал MailService. Заказ от этого не страдает: статус видно на сайте
            log.warn("Письмо по заказу {} не отправлено", order.number());
        }
    }
}
