package dev.pcshop.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Map;

// Модели ЮKassa
public final class YooKassa {

    private YooKassa() {
    }

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

        public static Confirmation redirect(String returnUrl) {
            return new Confirmation("redirect", returnUrl, null);
        }
    }

    public record CreatePayment(Amount amount, boolean capture, Confirmation confirmation, String description,
                                Map<String, String> metadata) {
    }

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

        public boolean refunded() {
            return refundedAmount != null && refundedAmount.decimal().signum() > 0;
        }

        public String confirmationUrl() {
            return confirmation == null ? null : confirmation.confirmationUrl();
        }

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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Shop(@JsonProperty("account_id") String accountId, boolean test, String status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Notification(String event, Payment object) {
    }
}
