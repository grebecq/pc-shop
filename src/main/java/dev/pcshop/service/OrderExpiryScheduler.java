package dev.pcshop.service;

import dev.pcshop.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@RequiredArgsConstructor
public class OrderExpiryScheduler {

    private final OrderRepository orders;
    private final OrderService service;
    private final PaymentService payments;
    @Value("${shop.orders.payment-timeout:30m}")
    private final Duration timeout;

    @Scheduled(fixedDelayString = "${shop.orders.expiry-check:60s}")
    public void cancelExpired() {
        for (Long id : orders.findAwaitingCreatedBefore(Instant.now().minus(timeout))) {
            try {
                if (payments.mayExpire(id)) {
                    service.cancelUnpaid(id);
                }
            } catch (ObjectOptimisticLockingFailureException e) {
                // Заказ в этот момент оплатили или изменили в админке — значит, отменять не нужно
                log.info("Заказ {} изменился во время автоотмены, пропускаем", id);
            }
        }
    }
}
