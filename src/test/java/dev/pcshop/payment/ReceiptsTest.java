package dev.pcshop.payment;

import dev.pcshop.config.ShopProperties;
import dev.pcshop.dto.request.CustomerRequest;
import dev.pcshop.entity.Order;
import dev.pcshop.enums.Channel;
import dev.pcshop.enums.DeliveryMethod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptsTest {

    @Test
    void discountIsSpreadSoReceiptMatchesPaymentToTheKopeck() {
        Order order = order();
        order.addItem(1, "Процессор", 19990, 1);
        order.addItem(2, "Память", 4990, 3);
        order.applyPromo("SALE", 1000);
        order.chargeDelivery(490);

        var items = receipts(11, true).items(order, Receipts.PREPAYMENT);

        assertThat(total(items)).isEqualByComparingTo(BigDecimal.valueOf(order.getTotal()));
        assertThat(items).extracting(YooKassa.ReceiptItem::description).containsExactly("Процессор", "Память", "Память", "Доставка");
        assertThat(items).extracting(YooKassa.ReceiptItem::quantity).containsExactly("1.00", "1.00", "2.00", "1.00");
        assertThat(items).allMatch(i -> i.vatCode() == 12 && i.paymentMode().equals("full_prepayment"));
        assertThat(items.getLast().paymentSubject()).isEqualTo("service");
        assertThat(items.getLast().amount().value()).isEqualTo("490.00");
    }

    @Test
    void settlementReceiptUsesPlainRateAndFullPayment() {
        Order order = order();
        order.addItem(1, "Видеокарта", 59990, 1);
        order.attachPayment("pay-1");

        var receipt = receipts(11, true).settlement(order);

        assertThat(receipt.paymentId()).isEqualTo("pay-1");
        assertThat(receipt.items()).singleElement().satisfies(i -> {
            assertThat(i.vatCode()).isEqualTo(11);
            assertThat(i.paymentMode()).isEqualTo("full_payment");
            assertThat(i.amount().value()).isEqualTo("59990.00");
        });
        assertThat(receipt.settlements()).singleElement()
                .satisfies(s -> assertThat(s.amount().value()).isEqualTo("59990.00"));
        assertThat(receipt.customer().phone()).isEqualTo("79000000000");
    }

    @Test
    void withoutReceiptsNothingIsSent() {
        Order order = order();
        order.addItem(1, "Корпус", 5990, 1);
        assertThat(receipts(1, false).prepayment(order)).isNull();
    }

    @Test
    void phoneIsSentInInternationalFormat() {
        assertThat(Receipts.phone("8 (900) 000-00-00")).isEqualTo("79000000000");
        assertThat(Receipts.phone("9000000000")).isEqualTo("79000000000");
        assertThat(Receipts.phone("+375 29 000-00-00")).isEqualTo("375290000000");
    }

    private static BigDecimal total(List<YooKassa.ReceiptItem> items) {
        return items.stream().map(i -> i.amount().decimal().multiply(new BigDecimal(i.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Order order() {
        return new Order(Channel.SITE, new CustomerRequest("Покупатель", "+7 900 000-00-00", "Москва", null),
                DeliveryMethod.COURIER, null, null);
    }

    private static Receipts receipts(int vat, boolean on) {
        return new Receipts(new ShopProperties("http://localhost:8080", null, null, null,
                new ShopProperties.Yookassa("123456", "key", "https://api.yookassa.ru/v3", on, vat, null)));
    }
}
