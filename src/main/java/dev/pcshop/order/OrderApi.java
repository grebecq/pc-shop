package dev.pcshop.order;

import dev.pcshop.account.AccountException;
import dev.pcshop.account.AccountService;
import dev.pcshop.account.AddressForm;
import dev.pcshop.account.ShopUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final AccountService accounts;

    OrderApi(OrderService orders, AccountService accounts) {
        this.orders = orders;
        this.accounts = accounts;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CreatedDto create(@Valid @RequestBody CreateOrderRequest request, @AuthenticationPrincipal ShopUser user) {
        Customer customer = request.customer();
        Long accountId = user == null ? null : user.getAccountId();
        if (accountId != null && request.addressId() != null) {
            // Сохранённый адрес берём из базы, а не из текста от браузера
            String address = accounts.address(accountId, request.addressId()).oneLine();
            customer = new Customer(customer.name(), customer.phone(), address, customer.comment());
        }

        Order order = orders.create(Channel.SITE, customer, request.items(), null, accountId);

        if (accountId != null) {
            accounts.rememberPhone(accountId, customer.phone());
        }
        if (accountId != null && request.addressId() == null && Boolean.TRUE.equals(request.saveAddress()) && request.newAddress() != null) {
            try {
                accounts.addAddress(accountId, request.newAddress());
            } catch (AccountException ignored) {
                // Заказ уже создан; если адрес не сохранился (лимит), покупатель добавит его в кабинете
            }
        }
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

    @ExceptionHandler(AccountException.class)
    ProblemDetail onAccountError(AccountException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail onInvalid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getDefaultMessage()).findFirst().orElse("Проверьте данные заказа");
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * @param addressId   сохранённый адрес вошедшего покупателя (тогда customer.address игнорируется)
     * @param newAddress  новый адрес по полям — сохраняется в кабинет, если saveAddress
     */
    record CreateOrderRequest(@Valid Customer customer,
                              @NotEmpty(message = "Корзина пуста") List<@Valid OrderLine> items,
                              Long addressId,
                              @Valid AddressForm newAddress,
                              Boolean saveAddress) {
    }

    record CreatedDto(UUID publicId, String number, int total, String paymentUrl) {
    }

    record ItemDto(long productId, String name, int price, int quantity) {
    }

    record OrderDto(UUID publicId, String number, OrderStatus status, String statusLabel, int total, List<ItemDto> items) {
    }
}
