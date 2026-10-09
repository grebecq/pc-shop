package dev.pcshop.service;

import dev.pcshop.config.ShopProperties;
import dev.pcshop.entity.Order;
import dev.pcshop.enums.OrderStatus;
import dev.pcshop.event.OrderEvents;
import dev.pcshop.exception.BadRequestException;
import dev.pcshop.payment.YooKassa;
import dev.pcshop.payment.YooKassaClient;
import dev.pcshop.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * Настоящая оплата через ЮKassa. Покупатель уходит с сайта на её страницу, платит картой, через СБП или SberPay
 * и возвращается. Номер карты наш сервер не видит.
 * <p>
 * Заказ становится оплаченным, только когда сам API ЮKassa отвечает «succeeded». Уведомлению (webhook) и возврату
 * покупателя на сайт на слово не верим: из них берём только id платежа и перепрашиваем платёж.
 * Деньги за отменённый заказ возвращаются автоматически.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    /** Ключ в metadata платежа: по нему уведомление находит заказ. */
    private static final String ORDER = "order";

    private final YooKassaClient yookassa;
    private final OrderService orders;
    private final OrderRepository orderRepository;
    private final ShopProperties props;

    public boolean online() {
        return props.yookassa().enabled();
    }

    /**
     * Ссылка на страницу оплаты ЮKassa. Пока покупатель не заплатил, повторный клик отдаёт тот же платёж, а не создаёт новый.
     *
     * @return null, если платить уже не нужно: заказ оплачен или отменён
     * @throws BadRequestException если ЮKassa не ответила — сообщение можно показать покупателю
     */
    public String start(UUID publicId) {
        Order order = orders.byPublicId(publicId);
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            return null;
        }
        try {
            if (order.getPaymentId() != null) {
                YooKassa.Payment current = yookassa.payment(order.getPaymentId());
                if (current.succeeded()) {
                    // Заплатил в другой вкладке, а уведомление ещё не дошло
                    sync(current);
                    return null;
                }
                if (current.pending() && current.confirmationUrl() != null) {
                    return current.confirmationUrl();
                }
                // Прошлый платёж отменён (отказ банка или покупатель закрыл страницу) — нужен новый
            }
            String returnUrl = props.baseUrl().replaceAll("/+$", "") + "/order/" + publicId + "?paid";
            var request = new YooKassa.CreatePayment(YooKassa.Amount.rub(order.getTotal()), true,
                    YooKassa.Confirmation.redirect(returnUrl), "Заказ " + order.number(), Map.of(ORDER, publicId.toString()));
            // Ключ зависит от заказа и прошлого платежа: двойной клик вернёт тот же платёж, а после отменённого создастся новый
            YooKassa.Payment payment = yookassa.createPayment(request, key(publicId + "/" + order.getPaymentId()));
            orders.attachPayment(order.getId(), payment.id());
            log.info("Заказ {}: создан платёж {} в ЮKassa", order.number(), payment.id());
            return payment.confirmationUrl();
        } catch (RestClientException e) {
            log.warn("Заказ {}: ЮKassa не создала платёж: {}", order.number(), e.getMessage());
            throw new BadRequestException("Платёжная система не ответила, попробуйте ещё раз через минуту");
        }
    }

    /**
     * Сверяет заказ с платежом по данным API ЮKassa: оплачивает заказ или возвращает деньги.
     * Вызывается из уведомления, при возврате покупателя на сайт, перед автоотменой и после отмены заказа.
     * Повторный вызов ничего не ломает. Платёж, которого ЮKassa не знает (чужой или выдуманный id), пропускаем.
     *
     * @throws RestClientException если ЮKassa не ответила: уведомление тогда придёт ещё раз
     */
    public void sync(String paymentId) {
        // Из уведомления id приходит от кого угодно: в адрес запроса пускаем только буквы, цифры и дефис
        if (paymentId == null || !paymentId.matches("[\\w-]{1,64}")) {
            return;
        }
        try {
            sync(yookassa.payment(paymentId));
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("ЮKassa не знает платёж {}", paymentId);
        } catch (ObjectOptimisticLockingFailureException e) {
            // Тот же заказ в ту же секунду сверял другой поток (уведомление и страница заказа) — сверяем ещё раз.
            // Повторный возврат не страшен: у него тот же Idempotence-Key
            sync(yookassa.payment(paymentId));
        }
    }

    private void sync(YooKassa.Payment payment) {
        if (!payment.succeeded()) {
            return;
        }
        Order order = orderFor(payment);
        if (order == null) {
            return;
        }
        if (payment.amount().decimal().compareTo(BigDecimal.valueOf(order.getTotal())) != 0) {
            log.error("Заказ {}: сумма платежа {} не совпадает с заказом, проверьте вручную", order.number(), payment.id());
            return;
        }
        order = orders.markPaidOnline(order.getId(), payment.id(), payment.methodLabel());
        boolean ownPayment = payment.id().equals(order.getPaymentId());
        // Заказ успели отменить или он уже оплачен другим платежом (заплатили дважды в двух вкладках)
        if (order.getStatus() == OrderStatus.CANCELLED || !ownPayment) {
            refund(order, payment, ownPayment);
        }
    }

    private Order orderFor(YooKassa.Payment payment) {
        String publicId = payment.metadata() == null ? null : payment.metadata().get(ORDER);
        try {
            Order order = publicId == null ? null : orderRepository.findByPublicId(UUID.fromString(publicId)).orElse(null);
            if (order == null) {
                log.warn("Платёж {}: заказ {} не найден", payment.id(), publicId);
            }
            return order;
        } catch (IllegalArgumentException e) {
            log.warn("Платёж {}: в metadata не номер заказа", payment.id());
            return null;
        }
    }

    private void refund(Order order, YooKassa.Payment payment, boolean ownPayment) {
        if (ownPayment && order.getRefundedAt() != null) {
            return;
        }
        // Если деньги уже возвращали (например, вручную в личном кабинете), второй раз не возвращаем
        if (!payment.refunded()) {
            yookassa.refund(new YooKassa.Refund(payment.id(), payment.amount()), key("refund/" + payment.id()));
            log.info("Заказ {}: вернули {} ₽ по платежу {}", order.number(), payment.amount().value(), payment.id());
        }
        if (ownPayment) {
            orders.markRefunded(order.getId());
        }
    }

    /**
     * Покупатель вернулся со страницы ЮKassa раньше уведомления: проверяем платёж сами.
     * Если ЮKassa не ответила, просто показываем заказ как есть — уведомление досчитает.
     */
    public Order refresh(Order order) {
        if (!online() || order.getStatus() != OrderStatus.AWAITING_PAYMENT || order.getPaymentId() == null) {
            return order;
        }
        try {
            sync(order.getPaymentId());
        } catch (RuntimeException e) {
            log.warn("Заказ {}: не получилось проверить оплату: {}", order.number(), e.getMessage());
            return order;
        }
        return orders.byPublicId(order.getPublicId());
    }

    /**
     * Перед автоотменой: вдруг покупатель как раз заплатил, а уведомление ещё не дошло.
     *
     * @return false — отменять нельзя: заказ оплачен или ЮKassa не ответила (проверим в следующий раз)
     */
    public boolean mayExpire(long orderId) {
        Order order = orders.byId(orderId);
        if (!online() || order.getPaymentId() == null) {
            return true;
        }
        try {
            sync(order.getPaymentId());
        } catch (RestClientException e) {
            log.warn("Заказ {}: ЮKassa не ответила, автоотмену отложим: {}", order.number(), e.getMessage());
            return false;
        }
        return orders.byId(orderId).getStatus() == OrderStatus.AWAITING_PAYMENT;
    }

    /**
     * Заказ отменили (покупатель, админка или таймаут) — если за него заплатили онлайн, возвращаем деньги.
     * После коммита и в отдельном потоке: ЮKassa может отвечать долго. Если возврат не прошёл, в админке
     * у заказа будет кнопка «Вернуть деньги».
     */
    @Async
    @TransactionalEventListener
    public void onStatusChanged(OrderEvents.StatusChanged event) {
        if (event.status() != OrderStatus.CANCELLED || !online()) {
            return;
        }
        Order order = orders.byId(event.orderId());
        if (order.getPaymentId() == null) {
            return;
        }
        try {
            sync(order.getPaymentId());
        } catch (RuntimeException e) {
            log.error("Заказ {}: не получилось вернуть деньги автоматически", order.number(), e);
        }
    }

    /** Кнопка «Вернуть деньги» в админке, если автоматический возврат не прошёл. */
    public void retryRefund(long orderId) {
        Order order = orders.byId(orderId);
        if (!online() || !order.awaitingRefund()) {
            return;
        }
        try {
            sync(order.getPaymentId());
        } catch (RestClientException e) {
            throw new BadRequestException("ЮKassa не ответила: " + e.getMessage());
        }
    }

    /** Проверка ключей со страницы настроек. */
    public YooKassa.Shop shop() {
        return yookassa.me();
    }

    /** Idempotence-Key для ЮKassa: один и тот же для одного и того же действия. */
    private static String key(String action) {
        return UUID.nameUUIDFromBytes(action.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
