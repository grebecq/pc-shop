package dev.pcshop.controller.web;

import dev.pcshop.config.ShopProperties;
import dev.pcshop.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Страницы для покупателя: доставка и оплата, гарантия и возврат, контакты, оферта, политика обработки данных. */
@Controller
@RequiredArgsConstructor
public class InfoController {

    private final DeliveryService delivery;
    private final ShopProperties props;

    @GetMapping("/delivery")
    public String delivery(Model model) {
        model.addAttribute("pickupPoints", delivery.pickupPoints());
        model.addAttribute("courierPrice", delivery.courierPrice());
        model.addAttribute("freeFrom", delivery.freeFrom());
        model.addAttribute("demoPayments", props.demoPayments());
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

    @GetMapping("/offer")
    public String offer(Model model) {
        model.addAttribute("siteUrl", props.siteUrl());
        return "info/offer";
    }

    @GetMapping("/privacy")
    public String privacy() {
        return "info/privacy";
    }
}
