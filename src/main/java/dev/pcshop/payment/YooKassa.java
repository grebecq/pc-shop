package dev.pcshop.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Map;

/** Запросы и ответы API ЮKassa (https://yookassa.ru/developers/api), только поля, которые нужны магазину. */
public final class YooKassa {

    private YooKassa() {
    }

    /** Сумма в рублях строкой с копейками: «1490.00». */
    public record Amount(String value, String currency) {

        public static Amount rub(int rubles) {
            return new Amount(rubles + ".00", "RUB");
        }

        public BigDecimal decimal() {
            return new BigDecimal(value);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Confirmation(String type,
                               @JsonProperty("return_url") String returnUrl,
                               @JsonProperty("confirmation_url") String confirmationUrl) {

        /** Покупатель уходит на страницу ЮKassa и после оплаты возвращается на returnUrl. */
        public static Confirmation redirect(String returnUrl) {
            return new Confirmation("redirect", returnUrl, null);
        }
    }

    /** capture = true: деньги списываются сразу, без отдельного подтверждения магазином. */
    public record CreatePayment(Amount amount, boolean capture, Confirmation confirmation, String description,
                                Map<String, String> metadata) {
    }

    /**
     * @param status   pending, waiting_for_capture, succeeded или canceled
     * @param metadata то, что магазин передал при создании платежа: здесь номер заказа
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payment(String id, String status, Amount amount, Confirmation confirmation,
                          @JsonProperty("payment_method") PaymentMethod paymentMethod,
                          @JsonProperty("refunded_amount") Amount refundedAmount,
                          Map<String, String> metadata) {

        public boolean succeeded() {
            return "succeeded".equals(status);
        }

        public boolean pending() {
            return "pending".equals(status);
        }

        /** Деньги уже возвращали: автоматически или вручную в личном кабинете. */
        public boolean refunded() {
            return refundedAmount != null && refundedAmount.decimal().signum() > 0;
        }

        /** Ссылка на страницу оплаты, пока платёж ждёт покупателя. */
        public String confirmationUrl() {
            return confirmation == null ? null : confirmation.confirmationUrl();
        }

        /** Чем заплатили, для страницы заказа: «Visa •• 4242», «СБП», «SberPay». */
        public String methodLabel() {
            if (paymentMethod == null) {
                return "ЮKassa";
            }
            if (paymentMethod.card() != null && paymentMethod.card().last4() != null) {
                return paymentMethod.card().brand() + " •• " + paymentMethod.card().last4();
            }
            return switch (String.valueOf(paymentMethod.type())) {
                case "sbp" -> "СБП";
                case "sberbank" -> "SberPay";
                case "tinkoff_bank" -> "T-Pay";
                case "yoo_money" -> "ЮMoney";
                default -> "ЮKassa";
            };
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentMethod(String type, Card card) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Card(String last4, @JsonProperty("card_type") String cardType) {

        String brand() {
            return switch (String.valueOf(cardType)) {
                case "MasterCard" -> "Mastercard";
                case "Mir" -> "МИР";
                case "null", "Unknown" -> "Карта";
                default -> cardType;
            };
        }
    }

    public record Refund(@JsonProperty("payment_id") String paymentId, Amount amount) {
    }

    /** Магазин, которому принадлежат ключи. test = true — тестовый магазин, деньги не настоящие. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Shop(@JsonProperty("account_id") String accountId, boolean test, String status) {
    }

    /** HTTP-уведомление: event — например, payment.succeeded, object — платёж. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Notification(String event, Payment object) {
    }
}
