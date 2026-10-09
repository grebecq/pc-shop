package dev.pcshop.controller.web;

import dev.pcshop.dto.request.CardPaymentRequest;
import dev.pcshop.entity.Order;
import dev.pcshop.enums.OrderStatus;
import dev.pcshop.exception.BadRequestException;
import dev.pcshop.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.Duration;
import java.util.UUID;

/**
 * Тестовая оплата картой на сайте: деньги не списываются, «банк» имитирует DemoPaymentGateway.
 * В боевом проекте вместо этой формы будет страница или виджет ЮKassa / CloudPayments, а оплату подтвердит их webhook.
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
        model.addAttribute("form", CardPaymentRequest.empty());
        return payPage(order, model);
    }

    @PostMapping("/pay/{publicId}")
    public String pay(@PathVariable UUID publicId, @Valid @ModelAttribute("form") CardPaymentRequest form,
                      BindingResult errors, Model model) {
        Order order = orders.byPublicId(publicId);
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            // Повторная отправка формы или заказ уже отменён по таймауту: карту не трогаем
            return afterPayment(order);
        }
        if (!errors.hasErrors()) {
            try {
                return afterPayment(orders.payByCard(publicId, form));
            } catch (BadRequestException e) {
                model.addAttribute("error", e.getMessage());
            }
        }
        // Номер, срок и имя остаются в форме, CVC шаблон обратно не выводит
        return payPage(order, model);
    }

    private String payPage(Order order, Model model) {
        model.addAttribute("order", order);
        model.addAttribute("payBefore", order.payBefore(timeout));
        return "shop/pay";
    }

    /** «Оплата прошла» показываем, только если заказ действительно оплачен (а не отменён по таймауту). */
    private static String afterPayment(Order order) {
        return "redirect:/order/" + order.getPublicId() + (order.getStatus() == OrderStatus.PAID ? "?paid" : "");
    }
}
