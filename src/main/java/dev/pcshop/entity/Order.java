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

    /** Адрес доставки или магазин самовывоза, одной строкой. */
    @Column(nullable = false)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_method", nullable = false)
    private DeliveryMethod deliveryMethod = DeliveryMethod.COURIER;

    /** Цена доставки на момент заказа. Уже входит в total. */
    @Column(name = "delivery_price", nullable = false)
    private int deliveryPrice;

    /** Промокод, по которому дали скидку, или null. */
    @Column(name = "promo_code")
    private String promoCode;

    /** Скидка по промокоду в рублях. Уже вычтена из total. */
    @Column(nullable = false)
    private int discount;

    /** Куда слать письма о заказе. У заказов из бота пусто. */
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

    /** Чем оплачен на сайте: «Visa •• 4242», «СБП», «SberPay». Номер карты целиком не храним. */
    @Column(name = "payment_card")
    private String paymentCard;

    /** Последний платёж в ЮKassa: им заказ оплачен или по нему покупатель сейчас платит. */
    @Column(name = "payment_id")
    private String paymentId;

    /** Когда вернули деньги за отменённый заказ, оплаченный через ЮKassa. */
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

    /** Скидка по промокоду считается от суммы товаров, поэтому вызывается после addItem. */
    public void applyPromo(String code, int discount) {
        if (discount < 0 || discount > itemsTotal()) {
            throw new IllegalArgumentException("Скидка не может быть больше суммы товаров");
        }
        total -= discount - this.discount;
        this.discount = discount;
        this.promoCode = code;
    }

    /** Цена доставки считается от суммы товаров, поэтому вызывается после addItem. */
    public void chargeDelivery(int price) {
        total += price - deliveryPrice;
        deliveryPrice = price;
    }

    /** Сумма товаров по их ценам, без скидки по промокоду и без доставки. */
    public int itemsTotal() {
        return total - deliveryPrice + discount;
    }

    /** Покупатель может сам отменить заказ, пока его не передали в доставку. */
    public boolean cancellableByCustomer() {
        return status == OrderStatus.AWAITING_PAYMENT || status == OrderStatus.PAID || status == OrderStatus.ASSEMBLING;
    }

    /** Статус для покупателя: при самовывозе «передан в доставку» значит, что заказ ждёт в магазине. */
    public String statusLabel() {
        return status == OrderStatus.SHIPPED && deliveryMethod == DeliveryMethod.PICKUP ? "Ждёт в магазине" : status.label();
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

    /** Тестовая оплата картой на сайте: то же, что markPaid, плюс запоминаем карту для страницы заказа. */
    public boolean markPaidByCard(String cardBrand, String cardLast4) {
        // «Visa •• 4242», а у неизвестной платёжной системы просто «•• 4242»
        return markPaidWith((cardBrand == null ? "" : cardBrand + " ") + "•• " + cardLast4);
    }

    /** Покупатель перешёл к оплате в ЮKassa. Прошлый платёж, если был, он так и не оплатил. */
    public void attachPayment(String paymentId) {
        this.paymentId = paymentId;
    }

    /** Оплата через ЮKassa этим платежом. */
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

    /**
     * Деньги пришли через ЮKassa. Способ оплаты записывает только markPaidOnline, поэтому заказ,
     * отмеченный оплаченным в админке, сюда не попадает, даже если покупатель начинал платить онлайн.
     */
    public boolean paidOnline() {
        return paidAt != null && paymentId != null && paymentCard != null;
    }

    /** Заказ отменили после онлайн-оплаты, а деньги ещё не вернули. */
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
            // Иначе заказ снова попадёт под автоотмену и вернёт на склад уже отданный товар
            throw new OrderException("Вернуть заказ в «ждёт оплаты» нельзя");
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
        return number(id);
    }

    /** Номер заказа для людей по его id: в письмах, в боте, в журнале склада. */
    public static String number(long id) {
        return "№" + (1000 + id);
    }
}
