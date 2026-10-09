package dev.pcshop.service;

import dev.pcshop.dto.request.CreateOrderRequest;
import dev.pcshop.dto.request.CustomerRequest;
import dev.pcshop.entity.Order;
import dev.pcshop.enums.Channel;
import dev.pcshop.enums.DeliveryMethod;
import dev.pcshop.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Оформление заказа на сайте: сам заказ плюс данные кабинета (телефон, новый адрес).
 * Без общей транзакции: если адрес не сохранится, уже созданный заказ откатываться не должен.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private final OrderService orderService;
    private final AccountService accountService;
    private final DeliveryService deliveryService;

    public Order checkout(CreateOrderRequest request, Long accountId) {
        if (accountId == null && !request.hasConsent()) {
            throw new BadRequestException("Нужно согласие на обработку персональных данных");
        }
        CustomerRequest customer = request.customer();

        // Магазин и сохранённый адрес берём из базы, а не из текста от браузера
        String address = customer.address();
        if (request.delivery() == DeliveryMethod.PICKUP) {
            address = deliveryService.pickupPoint(request.pickupPointId()).oneLine();
        } else if (accountId != null && request.addressId() != null) {
            address = accountService.address(accountId, request.addressId()).oneLine();
        }
        // Вошедшему без email в форме письма о заказе идут на email аккаунта
        String email = customer.email() == null && accountId != null ? accountService.get(accountId).getEmail() : customer.email();
        customer = customer.withAddressAndEmail(address, email);

        Order order = orderService.create(Channel.SITE, customer, request.delivery(), request.promoCode(),
                request.items(), null, accountId);

        if (accountId != null) {
            accountService.rememberPhone(accountId, customer.phone());
            if (request.wantsToSaveAddress()) {
                try {
                    accountService.addAddress(accountId, request.newAddress());
                } catch (BadRequestException e) {
                    // Заказ уже создан; если адрес не сохранился (лимит), покупатель добавит его в кабинете
                    log.info("Адрес из заказа {} не сохранён: {}", order.number(), e.getMessage());
                }
            }
        }
        return order;
    }
}
