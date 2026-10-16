package dev.pcshop.controller.web;

import dev.pcshop.config.ShopProperties;
import dev.pcshop.dto.request.CardPaymentRequest;
import dev.pcshop.entity.Order;
import dev.pcshop.enums.OrderStatus;
import dev.pcshop.exception.BadRequestException;
import dev.pcshop.service.OrderService;
import dev.pcshop.service.PaymentService;
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

@Controller
@RequiredArgsConstructor
public class PaymentController {

    private final OrderService orders;
    private final PaymentService payments;
    private final ShopProperties props;
    @Value("${shop.orders.payment-timeout:30m}")
    private final Duration timeout;

    @GetMapping("/pay/{publicId}")
    public String page(@PathVariable UUID publicId, Model model) {
        Order order = orders.byPublicId(publicId);
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            return "redirect:/order/" + publicId;
        }
        if (props.demoPayments()) {
            model.addAttribute("form", CardPaymentRequest.empty());
        }
        return payPage(order, model);
    }

    @PostMapping("/pay/{publicId}/online")
    public String online(@PathVariable UUID publicId, Model model) {
        if (!payments.online()) {
            return "redirect:/pay/" + publicId;
        }
        try {
            String url = payments.start(publicId);
            return url != null ? "redirect:" + url : afterPayment(orders.byPublicId(publicId));
        } catch (BadRequestException e) {
            model.addAttribute("error", e.getMessage());
            return payPage(orders.byPublicId(publicId), model);
        }
    }

    @PostMapping("/pay/{publicId}")
    public String pay(@PathVariable UUID publicId, @Valid @ModelAttribute("form") CardPaymentRequest form,
                      BindingResult errors, Model model) {
        Order order = orders.byPublicId(publicId);
        if (!props.demoPayments()) {
            return "redirect:/pay/" + publicId;
        }
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {

            return afterPayment(order);
        }
        if (!errors.hasErrors()) {
            try {
                return afterPayment(orders.payByCard(publicId, form));
            } catch (BadRequestException e) {
                model.addAttribute("error", e.getMessage());
            }
        }

        return payPage(order, model);
    }

    private String payPage(Order order, Model model) {
        model.addAttribute("order", order);
        model.addAttribute("online", payments.online());
        model.addAttribute("payBefore", order.payBefore(timeout));
        return "shop/pay";
    }

    private static String afterPayment(Order order) {
        return "redirect:/order/" + order.getPublicId() + (order.getStatus() == OrderStatus.PAID ? "?paid" : "");
    }
}
