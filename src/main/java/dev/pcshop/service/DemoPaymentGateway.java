package dev.pcshop.service;

import dev.pcshop.dto.request.CardPaymentRequest;
import dev.pcshop.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Имитация банка для тестовой оплаты картой: деньги не списываются, ни к какому счёту не привязано.
 * В боевом проекте здесь будет ЮKassa / CloudPayments: карту вводят на их странице или в их виджете,
 * а нам приходит только результат, платёжная система и последние 4 цифры.
 */
@Slf4j
@Service
public class DemoPaymentGateway {

    /** Тестовая карта, по которой «банк» всегда отказывает. */
    public static final String DECLINED_CARD = "4000000000000002";

    /** Что можно сохранить в заказе после оплаты. */
    public record PaidCard(String brand, String last4) {
    }

    /**
     * Списывает сумму с карты, данные которой уже проверены (формат, контрольная цифра, срок).
     *
     * @throws BadRequestException если банк отклонил платёж — сообщение можно показать покупателю
     */
    public PaidCard charge(CardPaymentRequest card, int amount) {
        String brand = brand(card.number());
        if (DECLINED_CARD.equals(card.number())) {
            log.info("Тестовый банк отклонил платёж {} ₽ по карте {} •• {}", amount, brand, card.last4());
            throw new BadRequestException("Банк отклонил платёж: недостаточно средств");
        }
        return new PaidCard(brand, card.last4());
    }

    /** Платёжная система по первым цифрам номера. */
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
        return "Карта";
    }
}
