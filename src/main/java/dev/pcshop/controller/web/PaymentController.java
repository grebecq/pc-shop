package dev.pcshop.controller.web;

import dev.pcshop.entity.Order;
import dev.pcshop.enums.OrderStatus;
import dev.pcshop.service.OrderService;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class PaymentController {

    private final OrderService orders;
    @Value("${shop.orders.payment-timeout:30m}")
    private final Duration timeout;

    @GetMapping("/pay/{publicId}")
    public String page(@PathVariable UUID publicId, Model model) {
        Order order = orders.byPublicId(publicId);
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            return "redirect:/order/" + publicId;
        }
        model.addAttribute("order", order);
        model.addAttribute("payBefore", order.payBefore(timeout));
        return "shop/pay";
    }

    @PostMapping("/pay/{publicId}")
    public String pay(@PathVariable UUID publicId) {
        Order order = orders.markPaid(publicId);
        // «Оплата прошла» показываем, только если заказ действительно оплачен (а не отменён по таймауту)
        return "redirect:/order/" + publicId + (order.getStatus() == OrderStatus.PAID ? "?paid" : "");
    }
}
