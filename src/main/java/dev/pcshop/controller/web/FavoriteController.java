package dev.pcshop.controller.web;

import dev.pcshop.security.ShopUser;
import dev.pcshop.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/** Избранное в личном кабинете. Сердечки в карточках переключаются без перезагрузки страницы. */
@Controller
@RequestMapping("/account/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favorites;

    @GetMapping
    public String list(@AuthenticationPrincipal ShopUser user, Model model) {
        model.addAttribute("products", favorites.products(user.getAccountId()));
        return "account/favorites";
    }

    /** Ответ для JS: новое состояние сердечка. */
    @PostMapping("/{productId}")
    @ResponseBody
    public Map<String, Boolean> toggle(@PathVariable long productId, @AuthenticationPrincipal ShopUser user) {
        return Map.of("favorite", favorites.toggle(user.getAccountId(), productId));
    }
}
