package dev.pcshop.entity;

import dev.pcshop.dto.request.CustomerRequest;
import dev.pcshop.enums.Channel;
import dev.pcshop.enums.OrderStatus;
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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Заказ с сайта или из бота. Статус меняется только через методы ниже, чтобы не нарушить правила перехода. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "customer_order")
@EntityListeners(AuditingEntityListener.class)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Для ссылок покупателю: по нему нельзя перебрать чужие заказы. */
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

    @Column(name = "paid_at")
    private Instant paidAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<OrderItem> items = new ArrayList<>();

    public Order(Channel channel, CustomerRequest customer, Long telegramChatId, Long accountId) {
        this.accountId = accountId;
        this.channel = channel;
        this.customerName = customer.name();
        this.phone = customer.phone();
        this.address = customer.address();
        this.comment = customer.comment();
        this.telegramChatId = telegramChatId;
    }

    public void addItem(long productId, String name, int price, int quantity) {
        items.add(new OrderItem(this, productId, name, price, quantity));
        total += price * quantity;
    }

    /** Возвращает true, если статус действительно поменялся (повторная оплата не считается). */
    public boolean markPaid() {
        if (status != OrderStatus.AWAITING_PAYMENT) {
            return false;
        }
        status = OrderStatus.PAID;
        paidAt = Instant.now();
        return true;
    }

    public void changeStatus(OrderStatus next) {
        if (status.isFinal()) {
            throw new IllegalStateException("Заказ уже " + status.label().toLowerCase() + ", статус менять нельзя");
        }
        if (next == OrderStatus.AWAITING_PAYMENT) {
            // Иначе заказ снова попадёт под автоотмену и вернёт на склад уже отданный товар
            throw new IllegalStateException("Вернуть заказ в «ждёт оплаты» нельзя");
        }
        if (next == OrderStatus.PAID && paidAt == null) {
            paidAt = Instant.now();
        }
        status = next;
    }

    /** До какого момента нужно оплатить, иначе заказ отменится автоматически. */
    public Instant payBefore(Duration timeout) {
        return createdAt.plus(timeout);
    }

    /** Короткий номер для людей: «№1042». */
    public String number() {
        return "№" + (1000 + id);
    }
}
