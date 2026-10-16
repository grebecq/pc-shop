package dev.pcshop.service;

import dev.pcshop.config.ShopProperties;
import dev.pcshop.dto.request.CardPaymentRequest;
import dev.pcshop.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// Оплата без денег
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoPaymentGateway {

    public static final String DECLINED_CARD = "4000000000000002";

    public record PaidCard(String brand, String last4) {
    }

    private final ShopProperties props;

    public PaidCard charge(CardPaymentRequest card, int amount) {
        if (!props.demoPayments()) {
            throw new BadRequestException("Тестовая оплата выключена: магазин принимает настоящие платежи");
        }
        String brand = brand(card.number());
        if (DECLINED_CARD.equals(card.number())) {
            log.info("Тестовый банк отклонил платёж {} ₽ по карте •• {}", amount, card.last4());
            throw new BadRequestException("Банк отклонил платёж: недостаточно средств");
        }
        return new PaidCard(brand, card.last4());
    }

    static String brand(String number) {
        if (number.startsWith("4")) {
            return "Visa";
        }
        if (number.startsWith("5")) {
            return "Mastercard";
        }
        int prefix = number.length() >= 4 ? Integer.parseInt(number.substring(0, 4)) : 0;
        if (prefix >= 2200 && prefix <= 2204) {
            return "МИР";
        }
        if (prefix >= 2221 && prefix <= 2720) {
            return "Mastercard";
        }
        return null;
    }
}
