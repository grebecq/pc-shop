package dev.pcshop.admin;

import dev.pcshop.catalog.ProductRepository;
import dev.pcshop.order.OrderService;
import dev.pcshop.order.OrderStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Админка владельца: заказы и товары. Доступ по логину и паролю из ADMIN_USER / ADMIN_PASSWORD. */
@Controller
@RequestMapping("/admin")
class AdminController {

    private final OrderService orders;
    private final ProductRepository products;

    AdminController(OrderService orders, ProductRepository products) {
        this.orders = orders;
        this.products = products;
    }

    @GetMapping
    String index() {
        return "redirect:/admin/orders";
    }

    @GetMapping("/orders")
    String orders(@RequestParam(required = false) OrderStatus status, @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("orders", orders.list(status, page));
        model.addAttribute("status", status);
        model.addAttribute("statuses", OrderStatus.values());
        return "admin/orders";
    }

    @GetMapping("/orders/{id}")
    String order(@PathVariable long id, Model model) {
        model.addAttribute("order", orders.byId(id));
        model.addAttribute("statuses", OrderStatus.values());
        return "admin/order";
    }

    @PostMapping("/orders/{id}/status")
    String changeStatus(@PathVariable long id, @RequestParam OrderStatus status, RedirectAttributes flash) {
        try {
            orders.changeStatus(id, status);
            flash.addFlashAttribute("message", "Статус изменён: " + status.label());
        } catch (IllegalStateException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/orders/" + id;
    }

    @GetMapping("/products")
    String products(Model model) {
        model.addAttribute("products", products.findAllByOrderByCategorySortOrderAscNameAsc());
        return "admin/products";
    }

    @PostMapping("/products/{id}")
    @Transactional
    public String updateProduct(@PathVariable long id, @RequestParam int price, @RequestParam int stock,
                                @RequestParam(defaultValue = "false") boolean active, RedirectAttributes flash) {
        var product = products.findById(id).orElseThrow();
        // Проверяем всё до изменений, чтобы не сохранить товар наполовину
        if (price <= 0 || stock < 0) {
            flash.addFlashAttribute("error", "Цена должна быть больше нуля, остаток не меньше нуля");
        } else {
            product.setPrice(price);
            product.setStock(stock);
            product.setActive(active);
            flash.addFlashAttribute("message", "Сохранено: " + product.getName());
        }
        return "redirect:/admin/products#p" + id;
    }
}
