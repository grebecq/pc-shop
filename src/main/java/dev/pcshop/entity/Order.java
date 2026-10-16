package dev.pcshop.entity;

import dev.pcshop.dto.request.CustomerRequest;
import dev.pcshop.enums.Channel;
import dev.pcshop.enums.DeliveryMethod;
import dev.pcshop.enums.OrderStatus;
import dev.pcshop.exception.OrderException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "customer_order")
@EntityListeners(AuditingEntityListener.class)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @Version
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.AWAITING_PAYMENT;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_method", nullable = false)
    private DeliveryMethod deliveryMethod = DeliveryMethod.COURIER;

    @Column(name = "delivery_price", nullable = false)
    private int deliveryPrice;

    @Column(name = "promo_code")
    private String promoCode;

    @Column(nullable = false)
    private int discount;

    private String email;

    private String comment;

    @Column(name = "telegram_chat_id")
    private Long telegramChatId;

    @Column(name = "account_id")
    private Long accountId;

    @Column(nullable = false)
    private int total;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "payment_card")
    private String paymentCard;

    @Column(name = "payment_id")
    private String paymentId;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<OrderItem> items = new ArrayList<>();

    public Order(Channel channel, CustomerRequest customer, DeliveryMethod deliveryMethod, Long telegramChatId, Long accountId) {
        this.accountId = accountId;
        this.channel = channel;
        this.customerName = customer.name();
        this.phone = customer.phone();
        this.address = customer.address();
        this.comment = customer.comment();
        this.email = customer.email();
        this.deliveryMethod = deliveryMethod;
        this.telegramChatId = telegramChatId;
    }

    public void addItem(long productId, String name, int price, int quantity) {
        items.add(new OrderItem(this, productId, name, price, quantity));
        total += price * quantity;
    }

    public void applyPromo(String code, int discount) {
        if (discount < 0 || discount > itemsTotal()) {
            throw new IllegalArgumentException("Скидка не может быть больше суммы товаров");
        }
        total -= discount - this.discount;
        this.discount = discount;
        this.promoCode = code;
    }

    public void chargeDelivery(int price) {
        total += price - deliveryPrice;
        deliveryPrice = price;
    }

    public int itemsTotal() {
        return total - deliveryPrice + discount;
    }

    public boolean cancellableByCustomer() {
        return status == OrderStatus.AWAITING_PAYMENT || status == OrderStatus.PAID || status == OrderStatus.ASSEMBLING;
    }

    public String statusLabel() {
        return status == OrderStatus.SHIPPED && deliveryMethod == DeliveryMethod.PICKUP ? "Ждёт в магазине" : status.label();
    }

    public boolean markPaid() {
        if (status != OrderStatus.AWAITING_PAYMENT) {
            return false;
        }
        status = OrderStatus.PAID;
        paidAt = Instant.now();
        return true;
    }

    public boolean markPaidByCard(String cardBrand, String cardLast4) {

        return markPaidWith((cardBrand == null ? "" : cardBrand + " ") + "•• " + cardLast4);
    }

    public void attachPayment(String paymentId) {
        this.paymentId = paymentId;
    }

    public boolean markPaidOnline(String paymentId, String method) {
        if (!markPaidWith(method)) {
            return false;
        }
        this.paymentId = paymentId;
        return true;
    }

    private boolean markPaidWith(String method) {
        if (!markPaid()) {
            return false;
        }
        paymentCard = method;
        return true;
    }

    public boolean paidOnline() {
        return paidAt != null && paymentId != null && paymentCard != null;
    }

    public boolean awaitingRefund() {
        return status == OrderStatus.CANCELLED && paidOnline() && refundedAt == null;
    }

    public void markRefunded() {
        if (refundedAt == null) {
            refundedAt = Instant.now();
        }
    }

    public void changeStatus(OrderStatus next) {
        if (status.isFinal()) {
            throw new OrderException("Заказ уже " + status.label().toLowerCase() + ", статус менять нельзя");
        }
        if (next == OrderStatus.AWAITING_PAYMENT) {

            throw new OrderException("Вернуть заказ в «ждёт оплаты» нельзя");
        }
        if (next == OrderStatus.PAID && paidAt == null) {
            paidAt = Instant.now();
        }
        status = next;
    }

    public Instant payBefore(Duration timeout) {
        return createdAt.plus(timeout);
    }

    public String number() {
        return number(id);
    }

    public static String number(long id) {
        return "№" + (1000 + id);
    }
}
