package dev.pcshop.service;

import dev.pcshop.dto.request.CreateOrderRequest;
import dev.pcshop.dto.request.CustomerRequest;
import dev.pcshop.entity.Order;
import dev.pcshop.enums.Channel;
import dev.pcshop.exception.AccountException;
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

    public Order checkout(CreateOrderRequest request, Long accountId) {
        CustomerRequest customer = request.customer();
        if (accountId != null && request.addressId() != null) {
            // Сохранённый адрес берём из базы, а не из текста от браузера
            String address = accountService.address(accountId, request.addressId()).oneLine();
            customer = new CustomerRequest(customer.name(), customer.phone(), address, customer.comment());
        }

        Order order = orderService.create(Channel.SITE, customer, request.items(), null, accountId);

        if (accountId != null) {
            accountService.rememberPhone(accountId, customer.phone());
            if (request.wantsToSaveAddress()) {
                try {
                    accountService.addAddress(accountId, request.newAddress());
                } catch (AccountException e) {
                    // Заказ уже создан; если адрес не сохранился (лимит), покупатель добавит его в кабинете
                    log.info("Адрес из заказа {} не сохранён: {}", order.number(), e.getMessage());
                }
            }
        }
        return order;
    }
}
