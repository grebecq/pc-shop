package dev.pcshop.service;

import dev.pcshop.dto.request.CardPaymentRequest;
import dev.pcshop.dto.request.CustomerRequest;
import dev.pcshop.dto.request.OrderLineRequest;
import dev.pcshop.entity.Order;
import dev.pcshop.entity.Product;
import dev.pcshop.enums.Channel;
import dev.pcshop.enums.OrderStatus;
import dev.pcshop.event.OrderEvents;
import dev.pcshop.exception.OrderException;
import dev.pcshop.exception.ResourceNotFoundException;
import dev.pcshop.repository.OrderRepository;
import dev.pcshop.repository.ProductRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Заказы с сайта и из бота. Данные покупателя проверяются здесь же (@Validated), поэтому
 * одни правила действуют для любого канала, даже если он сам что-то не проверил.
 */
@Slf4j
@Service
@Validated
@RequiredArgsConstructor
public class OrderService {

    private static final int MAX_POSITIONS = 30;

    private final OrderRepository orders;
    private final ProductRepository products;
    private final ApplicationEventPublisher events;
    private final DemoPaymentGateway paymentGateway;

    /**
     * Создаёт заказ и резервирует товар. Если чего-то не хватает, откатывается весь заказ целиком.
     */
    @Transactional
    public Order create(@NotNull(message = "Не указан канал заказа") Channel channel,
                        @NotNull(message = "Укажите данные покупателя") @Valid CustomerRequest customer,
                        @NotEmpty(message = "Корзина пуста") List<@NotNull @Valid OrderLineRequest> lines,
                        Long telegramChatId) {
        return create(channel, customer, lines, telegramChatId, null);
    }

    /** То же, но заказ привязывается к аккаунту покупателя на сайте. */
    @Transactional
    public Order create(@NotNull(message = "Не указан канал заказа") Channel channel,
                        @NotNull(message = "Укажите данные покупателя") @Valid CustomerRequest customer,
                        @NotEmpty(message = "Корзина пуста") List<@NotNull @Valid OrderLineRequest> lines,
                        Long telegramChatId, Long accountId) {
        // Одинаковые товары в запросе складываем в одну позицию
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        lines.forEach(l -> quantities.merge(l.productId(), l.quantity(), Integer::sum));
        if (quantities.isEmpty()) {
            throw new OrderException("Корзина пуста");
        }
        if (quantities.size() > MAX_POSITIONS) {
            throw new OrderException("Слишком много позиций в заказе");
        }

        Map<Long, Product> byId = products.findAllById(quantities.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        Order order = new Order(channel, customer, telegramChatId, accountId);
        quantities.forEach((productId, qty) -> {
            Product product = byId.get(productId);
            if (product == null || !product.isActive()) {
                throw new OrderException("Товар больше не продаётся");
            }
            if (products.reserve(productId, qty) == 0) {
                throw new OrderException("Недостаточно на складе: " + product.getName() + " (осталось " + product.getStock() + " шт.)");
            }
            order.addItem(productId, product.getName(), product.getPrice(), qty);
        });

        orders.save(order);
        log.info("Заказ {} создан: {}, {} поз., {} ₽", order.number(), channel, order.getItems().size(), order.getTotal());
        events.publishEvent(new OrderEvents.Created(order.getId()));
        return order;
    }

    @Transactional
    public Order markPaid(UUID publicId) {
        Order order = byPublicId(publicId);
        if (order.markPaid()) {
            log.info("Заказ {} оплачен", order.number());
            events.publishEvent(new OrderEvents.Paid(order.getId()));
        }
        return order;
    }

    /**
     * Тестовая оплата картой на сайте. Отменённый или уже оплаченный заказ не трогаем и деньги за него не списываем.
     * Если банк отказал, летит BadRequestException и заказ остаётся неоплаченным.
     */
    @Transactional
    public Order payByCard(UUID publicId, @NotNull(message = "Укажите данные карты") @Valid CardPaymentRequest card) {
        Order order = byPublicId(publicId);
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            return order;
        }
        var paid = paymentGateway.charge(card, order.getTotal());
        if (order.markPaidByCard(paid.brand(), paid.last4())) {
            log.info("Заказ {} оплачен картой {}", order.number(), order.getPaymentCard());
            events.publishEvent(new OrderEvents.Paid(order.getId()));
        }
        return order;
    }

    @Transactional
    public Order changeStatus(long orderId, OrderStatus status) {
        Order order = orders.findWithItemsById(orderId).orElseThrow(() -> new ResourceNotFoundException("Заказ не найден"));
        if (order.getStatus() == status) {
            return order;
        }
        order.changeStatus(status);
        log.info("Заказ {}: статус {}", order.number(), status);
        if (status == OrderStatus.CANCELLED) {
            // Отменённый заказ возвращает товар на склад
            order.getItems().forEach(i -> products.release(i.getProductId(), i.getQuantity()));
        }
        events.publishEvent(new OrderEvents.StatusChanged(order.getId(), status));
        return order;
    }

    /** Отмена неоплаченного заказа по таймауту. Если заказ уже оплачен, ничего не делает. */
    @Transactional
    public void cancelUnpaid(long orderId) {
        Order order = orders.findWithItemsById(orderId).orElseThrow(() -> new ResourceNotFoundException("Заказ не найден"));
        if (order.getStatus() == OrderStatus.AWAITING_PAYMENT) {
            changeStatus(orderId, OrderStatus.CANCELLED);
        }
    }

    @Transactional(readOnly = true)
    public Order byPublicId(UUID publicId) {
        return orders.findByPublicId(publicId).orElseThrow(() -> new ResourceNotFoundException("Заказ не найден"));
    }

    @Transactional(readOnly = true)
    public Order byId(long id) {
        return orders.findWithItemsById(id).orElseThrow(() -> new ResourceNotFoundException("Заказ не найден"));
    }

    @Transactional(readOnly = true)
    public List<Order> recentForChat(long chatId) {
        return orders.findTop5ByTelegramChatIdOrderByCreatedAtDesc(chatId);
    }

    @Transactional(readOnly = true)
    public Page<Order> forAccount(long accountId, int page) {
        return orders.findAllByAccountIdOrderByCreatedAtDesc(accountId, PageRequest.of(page, 10));
    }

    @Transactional(readOnly = true)
    public Page<Order> list(OrderStatus status, int page) {
        var pageable = PageRequest.of(page, 20);
        return status == null
                ? orders.findAllByOrderByCreatedAtDesc(pageable)
                : orders.findAllByStatusOrderByCreatedAtDesc(status, pageable);
    }
}
