package dev.pcshop.controller.web;

import dev.pcshop.dto.request.ProductUpdateRequest;
import dev.pcshop.enums.OrderStatus;
import dev.pcshop.service.CatalogService;
import dev.pcshop.service.OrderService;
import dev.pcshop.service.ProductPhotoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

/** Админка владельца: заказы и товары. Доступ по логину и паролю из ADMIN_USER / ADMIN_PASSWORD. */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final OrderService orders;
    private final CatalogService catalog;
    private final ProductPhotoService photos;

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
        } catch (IllegalStateException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/orders/" + id;
    }

    @GetMapping("/products")
    public String products(Model model) {
        model.addAttribute("products", catalog.allProducts());
        return "admin/products";
    }

    @PostMapping("/products/{id}")
    public String updateProduct(@PathVariable long id, @Valid @ModelAttribute ProductUpdateRequest request,
                                BindingResult binding, RedirectAttributes flash) {
        if (binding.hasErrors()) {
            flash.addFlashAttribute("error", binding.getAllErrors().getFirst().getDefaultMessage());
        } else {
            var product = catalog.updateProduct(id, request);
            flash.addFlashAttribute("message", "Сохранено: " + product.getName());
        }
        return "redirect:/admin/products#p" + id;
    }

    @PostMapping("/products/{id}/photo")
    public String uploadPhoto(@PathVariable long id, @RequestParam("photo") MultipartFile file, RedirectAttributes flash) {
        try {
            photos.save(id, file.getBytes());
            flash.addFlashAttribute("message", "Фото обновлено");
        } catch (IllegalArgumentException | IOException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/products#p" + id;
    }

    @PostMapping("/products/{id}/photo/delete")
    public String deletePhoto(@PathVariable long id, RedirectAttributes flash) {
        photos.delete(id);
        flash.addFlashAttribute("message", "Фото удалено");
        return "redirect:/admin/products#p" + id;
    }
}
