package dev.pcshop.order;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "customer_order")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID publicId = UUID.randomUUID();

    @Version
    private long version;

    @Enumerated(EnumType.STRING)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.AWAITING_PAYMENT;

    private String customerName;
    private String phone;
    private String address;
    private String comment;
    private Long telegramChatId;
    private Long accountId;
    private int total;
    private Instant createdAt = Instant.now();
    private Instant paidAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    Order(Channel channel, Customer customer, Long telegramChatId, Long accountId) {
        this.accountId = accountId;
        this.channel = channel;
        this.customerName = customer.name();
        this.phone = customer.phone();
        this.address = customer.address();
        this.comment = customer.comment();
        this.telegramChatId = telegramChatId;
    }

    void addItem(long productId, String name, int price, int quantity) {
        items.add(new OrderItem(this, productId, name, price, quantity));
        total += price * quantity;
    }

    /** Возвращает true, если статус действительно поменялся (повторная оплата не считается). */
    boolean markPaid() {
        if (status != OrderStatus.AWAITING_PAYMENT) {
            return false;
        }
        status = OrderStatus.PAID;
        paidAt = Instant.now();
        return true;
    }

    void changeStatus(OrderStatus next) {
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

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public Channel getChannel() { return channel; }
    public OrderStatus getStatus() { return status; }
    public String getCustomerName() { return customerName; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public String getComment() { return comment; }
    public Long getTelegramChatId() { return telegramChatId; }
    public Long getAccountId() { return accountId; }
    public int getTotal() { return total; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPaidAt() { return paidAt; }
    public List<OrderItem> getItems() { return items; }

    /** До какого момента нужно оплатить, иначе заказ отменится автоматически. */
    public Instant payBefore(java.time.Duration timeout) {
        return createdAt.plus(timeout);
    }

    /** Короткий номер для людей: «№1042». */
    public String number() {
        return "№" + (1000 + id);
    }
}
