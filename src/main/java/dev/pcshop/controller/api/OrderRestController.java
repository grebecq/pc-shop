package dev.pcshop.controller.api;

import dev.pcshop.dto.request.CreateOrderRequest;
import dev.pcshop.dto.response.OrderCreatedResponse;
import dev.pcshop.dto.response.OrderResponse;
import dev.pcshop.mapper.OrderMapper;
import dev.pcshop.security.ShopUser;
import dev.pcshop.service.CheckoutService;
import dev.pcshop.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Заказы", description = "Оформление заказа с сайта и его статус")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderRestController {

    private final CheckoutService checkoutService;
    private final OrderService orderService;
    private final OrderMapper orderMapper;

    @Operation(summary = "Оформить заказ",
            description = "Резервирует товар на складе. Если чего-то не хватает, заказ не создаётся целиком (409). "
                    + "Вошедший покупатель может передать addressId сохранённого адреса.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderCreatedResponse createOrder(@Valid @RequestBody CreateOrderRequest request,
                                            @Parameter(hidden = true) @AuthenticationPrincipal ShopUser user) {
        Long accountId = user == null ? null : user.getAccountId();
        return orderMapper.toCreatedResponse(checkoutService.checkout(request, accountId));
    }

    @Operation(summary = "Заказ по публичному id", description = "publicId — UUID из ссылки на оплату, перебрать чужие заказы по нему нельзя.")
    @GetMapping("/{publicId}")
    public OrderResponse getOrder(@PathVariable UUID publicId) {
        return orderMapper.toResponse(orderService.byPublicId(publicId));
    }
}
