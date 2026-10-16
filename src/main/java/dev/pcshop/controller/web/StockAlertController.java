package dev.pcshop.controller.web;

import dev.pcshop.exception.BadRequestException;
import dev.pcshop.security.ShopUser;
import dev.pcshop.service.CatalogService;
import dev.pcshop.service.StockAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class StockAlertController {

    private final CatalogService catalog;
    private final StockAlertService alerts;

    @PostMapping("/p/{slug}/stock-alert")
    public String toggle(@PathVariable String slug, @AuthenticationPrincipal ShopUser user, RedirectAttributes flash) {
        var product = catalog.product(slug);
        try {
            boolean on = alerts.toggle(user.getAccountId(), product.getId());
            flash.addFlashAttribute("stockMessage", on ? "Готово, напишем, когда товар появится" : "Больше не будем писать об этом товаре");
        } catch (BadRequestException e) {
            flash.addFlashAttribute("stockError", e.getMessage());
        }
        return "redirect:/p/" + slug;
    }
}
