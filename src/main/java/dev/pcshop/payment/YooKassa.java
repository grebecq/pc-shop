package dev.pcshop.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

// Модели ЮKassa
public final class YooKassa {

    private YooKassa() {
    }

    public record Amount(String value, String currency) {

        public static Amount rub(int rubles) {
            return new Amount(rubles + ".00", "RUB");
        }

        public static Amount kopecks(long kopecks) {
            return new Amount(BigDecimal.valueOf(kopecks, 2).toPlainString(), "RUB");
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreatePayment(Amount amount, boolean capture, Confirmation confirmation, String description,
                                Map<String, String> metadata, Receipt receipt) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Receipt(Customer customer, List<ReceiptItem> items,
                          @JsonProperty("tax_system_code") Integer taxSystemCode) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Customer(@JsonProperty("full_name") String fullName, String email, String phone) {
    }

    public record ReceiptItem(String description, String quantity, Amount amount,
                              @JsonProperty("vat_code") int vatCode,
                              @JsonProperty("payment_mode") String paymentMode,
                              @JsonProperty("payment_subject") String paymentSubject) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FinalReceipt(String type, @JsonProperty("payment_id") String paymentId, Customer customer,
                               List<ReceiptItem> items, List<Settlement> settlements, boolean send,
                               @JsonProperty("tax_system_code") Integer taxSystemCode) {
    }

    public record Settlement(String type, Amount amount) {
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Refund(@JsonProperty("payment_id") String paymentId, Amount amount, Receipt receipt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Shop(@JsonProperty("account_id") String accountId, boolean test, String status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Notification(String event, Payment object) {
    }
}
