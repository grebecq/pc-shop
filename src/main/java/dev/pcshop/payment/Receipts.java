package dev.pcshop.payment;

import dev.pcshop.config.ShopProperties;
import dev.pcshop.entity.Order;
import dev.pcshop.entity.OrderItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// Чеки 54-ФЗ
@Component
@RequiredArgsConstructor
public class Receipts {

    public static final String PREPAYMENT = "full_prepayment";
    public static final String PAYMENT = "full_payment";

    private static final int MAX_DESCRIPTION = 128;

    private final ShopProperties props;

    public boolean enabled() {
        return props.yookassa().sendsReceipts();
    }

    public YooKassa.Receipt prepayment(Order order) {
        return enabled() ? new YooKassa.Receipt(customer(order), items(order, PREPAYMENT), props.yookassa().taxSystemCode()) : null;
    }

    public YooKassa.FinalReceipt settlement(Order order) {
        return new YooKassa.FinalReceipt("payment", order.getPaymentId(), customer(order), items(order, PAYMENT),
                List.of(new YooKassa.Settlement("prepayment", YooKassa.Amount.rub(order.getTotal()))), true,
                props.yookassa().taxSystemCode());
    }

    List<YooKassa.ReceiptItem> items(Order order, String mode) {
        int vat = PREPAYMENT.equals(mode) ? prepaymentVat(props.yookassa().vat()) : props.yookassa().vat();
        List<OrderItem> lines = order.getItems();
        long itemsTotal = lines.stream().mapToLong(i -> (long) i.sum() * 100).sum();
        long discount = order.getDiscount() * 100L;

        long[] lineDiscount = new long[lines.size()];
        long given = 0;
        int largest = 0;
        for (int i = 0; i < lines.size(); i++) {
            long sum = lines.get(i).sum() * 100L;
            lineDiscount[i] = itemsTotal == 0 ? 0 : discount * sum / itemsTotal;
            given += lineDiscount[i];
            if (sum > lines.get(largest).sum() * 100L) {
                largest = i;
            }
        }
        if (!lines.isEmpty()) {
            lineDiscount[largest] += discount - given;
        }

        List<YooKassa.ReceiptItem> items = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            OrderItem line = lines.get(i);
            long rest = line.sum() * 100L - lineDiscount[i];
            int quantity = line.getQuantity();
            long unit = rest / quantity;
            int dearer = (int) (rest % quantity);
            if (quantity > dearer) {
                items.add(item(line.getName(), quantity - dearer, unit, vat, mode, "commodity"));
            }
            if (dearer > 0) {
                items.add(item(line.getName(), dearer, unit + 1, vat, mode, "commodity"));
            }
        }
        if (order.getDeliveryPrice() > 0) {
            items.add(item("Доставка", 1, order.getDeliveryPrice() * 100L, vat, mode, "service"));
        }
        return items;
    }

    static int prepaymentVat(int vat) {
        return switch (vat) {
            case 3 -> 5;
            case 4 -> 6;
            case 7 -> 9;
            case 8 -> 10;
            case 11 -> 12;
            default -> vat;
        };
    }

    private static YooKassa.ReceiptItem item(String name, int quantity, long unitKopecks, int vat, String mode, String subject) {
        String description = name.length() > MAX_DESCRIPTION ? name.substring(0, MAX_DESCRIPTION) : name;
        return new YooKassa.ReceiptItem(description, quantity + ".00", YooKassa.Amount.kopecks(unitKopecks), vat, mode, subject);
    }

    private static YooKassa.Customer customer(Order order) {
        return new YooKassa.Customer(order.getCustomerName(), order.getEmail(), phone(order.getPhone()));
    }

    static String phone(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() == 11 && digits.startsWith("8")) {
            return "7" + digits.substring(1);
        }
        if (digits.length() == 10 && digits.startsWith("9")) {
            return "7" + digits;
        }
        return digits.isEmpty() ? null : digits;
    }
}
