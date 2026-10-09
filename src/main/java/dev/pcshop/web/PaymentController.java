package dev.pcshop.web;

import dev.pcshop.order.Order;
import dev.pcshop.order.OrderService;
import dev.pcshop.order.OrderStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.Duration;
import java.util.UUID;

/**
 * Тестовая оплата на сайте. В боевом проекте здесь вместо формы будет редирект
 * на платёжную страницу ЮKassa / CloudPayments, а markPaid вызовет их webhook.
 */
@Controller
class PaymentController {

    private final OrderService orders;
    private final Duration timeout;

    PaymentController(OrderService orders, @Value("${shop.orders.payment-timeout:30m}") Duration timeout) {
        this.orders = orders;
        this.timeout = timeout;
    }

    @GetMapping("/pay/{publicId}")
    String page(@PathVariable UUID publicId, Model model) {
        Order order = orders.byPublicId(publicId);
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            return "redirect:/order/" + publicId;
        }
        model.addAttribute("order", order);
        model.addAttribute("payBefore", order.payBefore(timeout));
        return "shop/pay";
    }

    @PostMapping("/pay/{publicId}")
    String pay(@PathVariable UUID publicId) {
        Order order = orders.markPaid(publicId);
        // «Оплата прошла» показываем, только если заказ действительно оплачен (а не отменён по таймауту)
        return "redirect:/order/" + publicId + (order.getStatus() == OrderStatus.PAID ? "?paid" : "");
    }
}
