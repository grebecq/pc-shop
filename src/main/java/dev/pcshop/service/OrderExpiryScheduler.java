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

// Отмена неоплаченных заказов
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

                log.info("Заказ {} изменился во время автоотмены, пропускаем", id);
            }
        }
    }
}
