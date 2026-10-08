package dev.pcshop.web;

import dev.pcshop.order.Order;
import dev.pcshop.order.OrderService;
import dev.pcshop.order.OrderStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.UUID;

/**
 * Тестовая оплата на сайте. В боевом проекте здесь вместо формы будет редирект
 * на платёжную страницу ЮKassa / CloudPayments, а markPaid вызовет их webhook.
 */
@Controller
class PaymentController {

    private final OrderService orders;

    PaymentController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping("/pay/{publicId}")
    String page(@PathVariable UUID publicId, Model model) {
        Order order = orders.byPublicId(publicId);
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            return "redirect:/order/" + publicId;
        }
        model.addAttribute("order", order);
        return "shop/pay";
    }

    @PostMapping("/pay/{publicId}")
    String pay(@PathVariable UUID publicId) {
        orders.markPaid(publicId);
        return "redirect:/order/" + publicId + "?paid";
    }
}
