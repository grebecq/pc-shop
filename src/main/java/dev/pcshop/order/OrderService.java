package dev.pcshop.order;

import dev.pcshop.catalog.NotFoundException;
import dev.pcshop.catalog.Product;
import dev.pcshop.catalog.ProductRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final int MAX_POSITIONS = 30;

    private final OrderRepository orders;
    private final ProductRepository products;
    private final ApplicationEventPublisher events;

    OrderService(OrderRepository orders, ProductRepository products, ApplicationEventPublisher events) {
        this.orders = orders;
        this.products = products;
        this.events = events;
    }

    /**
     * Создаёт заказ и резервирует товар. Если чего-то не хватает, откатывается весь заказ целиком.
     */
    @Transactional
    public Order create(Channel channel, Customer customer, List<OrderLine> lines, Long telegramChatId) {
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

        Order order = new Order(channel, customer, telegramChatId);
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
        events.publishEvent(new OrderEvents.Created(order.getId()));
        return order;
    }

    @Transactional
    public Order markPaid(UUID publicId) {
        Order order = byPublicId(publicId);
        if (order.markPaid()) {
            events.publishEvent(new OrderEvents.Paid(order.getId()));
        }
        return order;
    }

    @Transactional
    public Order changeStatus(long orderId, OrderStatus status) {
        Order order = orders.findWithItemsById(orderId).orElseThrow(() -> new NotFoundException("Заказ не найден"));
        if (order.getStatus() == status) {
            return order;
        }
        order.changeStatus(status);
        if (status == OrderStatus.CANCELLED) {
            // Отменённый заказ возвращает товар на склад
            order.getItems().forEach(i -> products.release(i.getProductId(), i.getQuantity()));
        }
        events.publishEvent(new OrderEvents.StatusChanged(order.getId(), status));
        return order;
    }

    @Transactional(readOnly = true)
    public Order byPublicId(UUID publicId) {
        return orders.findByPublicId(publicId).orElseThrow(() -> new NotFoundException("Заказ не найден"));
    }

    @Transactional(readOnly = true)
    public Order byId(long id) {
        return orders.findWithItemsById(id).orElseThrow(() -> new NotFoundException("Заказ не найден"));
    }

    @Transactional(readOnly = true)
    public List<Order> recentForChat(long chatId) {
        return orders.findTop5ByTelegramChatIdOrderByCreatedAtDesc(chatId);
    }

    @Transactional(readOnly = true)
    public Page<Order> list(OrderStatus status, int page) {
        var pageable = PageRequest.of(page, 20);
        return status == null
                ? orders.findAllByOrderByCreatedAtDesc(pageable)
                : orders.findAllByStatusOrderByCreatedAtDesc(status, pageable);
    }
}
