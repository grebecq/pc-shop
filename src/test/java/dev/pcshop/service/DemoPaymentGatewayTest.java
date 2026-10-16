package dev.pcshop.service;

import dev.pcshop.config.ShopProperties;
import dev.pcshop.dto.request.CardPaymentRequest;
import dev.pcshop.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoPaymentGatewayTest {

    private final DemoPaymentGateway gateway = new DemoPaymentGateway(props(null, null));

    @Test
    void brandByFirstDigits() {
        assertThat(DemoPaymentGateway.brand("4242424242424242")).isEqualTo("Visa");
        assertThat(DemoPaymentGateway.brand("5555555555554444")).isEqualTo("Mastercard");
        assertThat(DemoPaymentGateway.brand("2221000000000009")).isEqualTo("Mastercard");
        assertThat(DemoPaymentGateway.brand("2720990000000000")).isEqualTo("Mastercard");
        assertThat(DemoPaymentGateway.brand("2200000000000004")).isEqualTo("МИР");
        assertThat(DemoPaymentGateway.brand("2204000000000000")).isEqualTo("МИР");
        assertThat(DemoPaymentGateway.brand("2205000000000000")).isNull();
        assertThat(DemoPaymentGateway.brand("2721000000000000")).isNull();
        assertThat(DemoPaymentGateway.brand("6011000000000004")).isNull();
    }

    @Test
    void chargeReturnsOnlyBrandAndLastFourDigits() {
        var paid = gateway.charge(new CardPaymentRequest("5555 5555 5555 4444", "12/40", "123", ""), 1000);
        assertThat(paid).isEqualTo(new DemoPaymentGateway.PaidCard("Mastercard", "4444"));
    }

    @Test
    void testCardIsDeclined() {
        assertThatThrownBy(() -> gateway.charge(new CardPaymentRequest("4000 0000 0000 0002", "12/40", "123", ""), 1000))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Банк отклонил платёж: недостаточно средств");
    }

    @Test
    void offWhenRealPaymentsConfigured() {
        var withYookassa = new DemoPaymentGateway(props("123456", "test_key"));
        assertThatThrownBy(() -> withYookassa.charge(new CardPaymentRequest("4242 4242 4242 4242", "12/40", "123", ""), 1000))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Тестовая оплата выключена");
    }

    private static ShopProperties props(String shopId, String secretKey) {
        return new ShopProperties("http://localhost:8080", null, new ShopProperties.Telegram(null, null, null, null),
                new ShopProperties.Delivery(490, 50000), new ShopProperties.Yookassa(shopId, secretKey, "https://api.yookassa.ru/v3", false, 1, null));
    }
}
