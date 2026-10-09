package dev.pcshop.controller.api;

import dev.pcshop.dto.request.CreateOrderRequest;
import dev.pcshop.dto.response.OrderCreatedResponse;
import dev.pcshop.dto.response.OrderResponse;
import dev.pcshop.mapper.OrderMapper;
import dev.pcshop.security.ShopUser;
import dev.pcshop.service.CheckoutService;
import dev.pcshop.service.OrderService;
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

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderRestController {

    private final CheckoutService checkoutService;
    private final OrderService orderService;
    private final OrderMapper orderMapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderCreatedResponse createOrder(@Valid @RequestBody CreateOrderRequest request,
                                            @AuthenticationPrincipal ShopUser user) {
        Long accountId = user == null ? null : user.getAccountId();
        return orderMapper.toCreatedResponse(checkoutService.checkout(request, accountId));
    }

    @GetMapping("/{publicId}")
    public OrderResponse getOrder(@PathVariable UUID publicId) {
        return orderMapper.toResponse(orderService.byPublicId(publicId));
    }
}
