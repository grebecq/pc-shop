package dev.pcshop.controller.web;

import dev.pcshop.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Страницы для покупателя: доставка и оплата, гарантия и возврат, контакты, политика обработки данных. */
@Controller
@RequiredArgsConstructor
public class InfoController {

    private final DeliveryService delivery;

    @GetMapping("/delivery")
    public String delivery(Model model) {
        model.addAttribute("pickupPoints", delivery.pickupPoints());
        model.addAttribute("courierPrice", delivery.courierPrice());
        model.addAttribute("freeFrom", delivery.freeFrom());
        return "info/delivery";
    }

    @GetMapping("/warranty")
    public String warranty() {
        return "info/warranty";
    }

    @GetMapping("/contacts")
    public String contacts(Model model) {
        model.addAttribute("pickupPoints", delivery.pickupPoints());
        return "info/contacts";
    }

    @GetMapping("/privacy")
    public String privacy() {
        return "info/privacy";
    }
}
