package dev.pcshop.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Отменяет заказы, которые не оплатили вовремя, и возвращает товар на склад.
 * Без этого любой посетитель мог бы «выкупить» весь остаток неоплаченными заказами.
 */
@Component
class OrderExpiry {

    private static final Logger log = LoggerFactory.getLogger(OrderExpiry.class);

    private final OrderRepository orders;
    private final OrderService service;
    private final Duration timeout;

    OrderExpiry(OrderRepository orders, OrderService service, @Value("${shop.orders.payment-timeout:30m}") Duration timeout) {
        this.orders = orders;
        this.service = service;
        this.timeout = timeout;
    }

    @Scheduled(fixedDelayString = "${shop.orders.expiry-check:60s}")
    void cancelExpired() {
        for (Long id : orders.findAwaitingCreatedBefore(Instant.now().minus(timeout))) {
            try {
                service.cancelUnpaid(id);
            } catch (ObjectOptimisticLockingFailureException e) {
                // Заказ в этот момент оплатили или изменили в админке — значит, отменять не нужно
                log.info("Заказ {} изменился во время автоотмены, пропускаем", id);
            }
        }
    }
}
