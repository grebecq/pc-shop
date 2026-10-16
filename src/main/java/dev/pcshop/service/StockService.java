package dev.pcshop.service;

import dev.pcshop.entity.Order;
import dev.pcshop.entity.StockMovement;
import dev.pcshop.enums.StockReason;
import dev.pcshop.event.ProductEvents;
import dev.pcshop.exception.BadRequestException;
import dev.pcshop.exception.ResourceNotFoundException;
import dev.pcshop.repository.ProductRepository;
import dev.pcshop.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class StockService {

    private static final int MAX_QUANTITY = 100_000;

    private final ProductRepository products;
    private final StockMovementRepository movements;
    private final ApplicationEventPublisher events;

    public StockMovement receive(long productId, int quantity, String comment) {
        check(productId, quantity);
        products.add(productId, quantity);
        return record(productId, StockReason.RECEIPT, quantity, null, comment);
    }

    public StockMovement writeOff(long productId, int quantity, String comment) {
        check(productId, quantity);
        if (products.take(productId, quantity) == 0) {
            throw new BadRequestException("Столько нет: на складе " + products.stockOf(productId) + " шт.");
        }
        return record(productId, StockReason.WRITE_OFF, -quantity, null, comment);
    }

    public StockMovement count(long productId, int actual, String comment) {
        if (actual < 0 || actual > MAX_QUANTITY) {
            throw new BadRequestException("Укажите остаток от 0 до " + MAX_QUANTITY);
        }

        int before = products.lockStock(productId).orElseThrow(() -> new ResourceNotFoundException("Товар не найден"));
        if (before == actual) {
            return null;
        }
        products.setStock(productId, actual);
        return record(productId, StockReason.COUNT, actual - before, null, comment);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean reserve(long productId, int quantity) {
        return products.reserve(productId, quantity) > 0;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void sold(Order order) {

        order.getItems().forEach(i -> record(i.getProductId(), StockReason.SALE, -i.getQuantity(), order.getId(), null));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void returned(Order order) {
        order.getItems().forEach(i -> {
            products.add(i.getProductId(), i.getQuantity());
            record(i.getProductId(), StockReason.RETURN, i.getQuantity(), order.getId(), null);
        });
    }

    @Transactional(readOnly = true)
    public List<StockMovement> history(long productId) {
        return movements.findTop50ByProductIdOrderByIdDesc(productId);
    }

    private void check(long productId, int quantity) {
        if (quantity <= 0 || quantity > MAX_QUANTITY) {
            throw new BadRequestException("Укажите количество от 1 до " + MAX_QUANTITY);
        }
        if (!products.existsById(productId)) {
            throw new ResourceNotFoundException("Товар не найден");
        }
    }

    private StockMovement record(long productId, StockReason reason, int delta, Long orderId, String comment) {
        int after = products.stockOf(productId);

        if (after > 0 && after - delta <= 0) {
            events.publishEvent(new ProductEvents.Restocked(productId));
        }
        return movements.save(new StockMovement(productId, reason, delta, after, orderId, comment));
    }
}
