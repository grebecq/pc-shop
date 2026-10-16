package dev.pcshop.controller.api;

import dev.pcshop.payment.YooKassa;
import dev.pcshop.service.PaymentService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

// Уведомления от ЮKassa
@Slf4j
@Hidden
@RestController
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PaymentService payments;

    @PostMapping("/api/payments/yookassa")
    public ResponseEntity<Void> yookassa(@RequestBody YooKassa.Notification notification) {
        if (!payments.online()) {
            return ResponseEntity.notFound().build();
        }

        if (notification.event() == null || !notification.event().startsWith("payment.") || notification.object() == null) {
            return ResponseEntity.ok().build();
        }
        try {
            payments.sync(notification.object().id());
        } catch (RestClientException e) {
            log.warn("Уведомление о платеже {}: ЮKassa не ответила, ждём повтора: {}", notification.object().id(), e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.ok().build();
    }
}
