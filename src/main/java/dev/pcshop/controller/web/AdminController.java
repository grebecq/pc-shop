package dev.pcshop.controller.web;

import dev.pcshop.enums.OrderStatus;
import dev.pcshop.exception.BadRequestException;
import dev.pcshop.exception.OrderException;
import dev.pcshop.service.OrderService;
import dev.pcshop.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;


/** Админка владельца: заказы (товары и категории — в AdminCatalogController). Доступ по логину и паролю из ADMIN_USER / ADMIN_PASSWORD. */
@PreAuthorize("hasRole('ADMIN')")
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final OrderService orders;
    private final PaymentService payments;

    @GetMapping
    public String index() {
        return "redirect:/admin/orders";
    }

    @GetMapping("/orders")
    public String orders(@RequestParam(required = false) OrderStatus status, @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("orders", orders.list(status, page));
        model.addAttribute("status", status);
        model.addAttribute("statuses", OrderStatus.values());
        return "admin/orders";
    }

    @GetMapping("/orders/{id}")
    public String order(@PathVariable long id, Model model) {
        model.addAttribute("order", orders.byId(id));
        model.addAttribute("statuses", OrderStatus.values());
        return "admin/order";
    }

    @PostMapping("/orders/{id}/status")
    public String changeStatus(@PathVariable long id, @RequestParam OrderStatus status, RedirectAttributes flash) {
        try {
            orders.changeStatus(id, status);
            flash.addFlashAttribute("message", "Статус изменён: " + status.label());
        } catch (OrderException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/orders/" + id;
    }

    @PostMapping("/orders/{id}/refund")
    public String refund(@PathVariable long id, RedirectAttributes flash) {
        try {
            payments.retryRefund(id);
            flash.addFlashAttribute("message", orders.byId(id).getRefundedAt() != null
                    ? "Деньги возвращены" : "Возврат не понадобился: ЮKassa не видит оплаты по этому заказу");
        } catch (BadRequestException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/orders/" + id;
    }
}
