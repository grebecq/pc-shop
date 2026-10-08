package dev.pcshop.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
class OrderApi {

    private final OrderService orders;

    OrderApi(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CreatedDto create(@Valid @RequestBody CreateOrderRequest request) {
        Order order = orders.create(Channel.SITE, request.customer(), request.items(), null);
        return new CreatedDto(order.getPublicId(), order.number(), order.getTotal(), "/pay/" + order.getPublicId());
    }

    @GetMapping("/{publicId}")
    OrderDto get(@PathVariable UUID publicId) {
        Order o = orders.byPublicId(publicId);
        return new OrderDto(o.getPublicId(), o.number(), o.getStatus(), o.getStatus().label(), o.getTotal(),
                o.getItems().stream().map(i -> new ItemDto(i.getProductId(), i.getName(), i.getPrice(), i.getQuantity())).toList());
    }

    @ExceptionHandler(OrderException.class)
    ProblemDetail onOrderError(OrderException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail onInvalid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getDefaultMessage()).findFirst().orElse("Проверьте данные заказа");
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
    }

    record CreateOrderRequest(@Valid Customer customer, @NotEmpty(message = "Корзина пуста") List<@Valid OrderLine> items) {
    }

    record CreatedDto(UUID publicId, String number, int total, String paymentUrl) {
    }

    record ItemDto(long productId, String name, int price, int quantity) {
    }

    record OrderDto(UUID publicId, String number, OrderStatus status, String statusLabel, int total, List<ItemDto> items) {
    }
}
