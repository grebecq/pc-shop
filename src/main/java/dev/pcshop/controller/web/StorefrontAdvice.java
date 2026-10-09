package dev.pcshop.controller.web;

import dev.pcshop.config.ShopProperties;
import dev.pcshop.security.ShopUser;
import dev.pcshop.service.CatalogService;
import dev.pcshop.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Set;

/** Общие данные шапки (категории, ссылка на бота, вошедший покупатель, его избранное) для всех страниц сайта. */
@ControllerAdvice(basePackageClasses = StorefrontAdvice.class)
@RequiredArgsConstructor
public class StorefrontAdvice {

    private final CatalogService catalog;
    private final ShopProperties props;
    private final FavoriteService favorites;

    @ModelAttribute
    public void common(Model model, @AuthenticationPrincipal ShopUser user) {
        model.addAttribute("categories", catalog.categories());
        model.addAttribute("botLink", props.telegram().botLink());
        model.addAttribute("user", user);
        // Чтобы в карточках товаров сразу были закрашены сердечки избранного
        model.addAttribute("favoriteIds", user == null ? Set.of() : favorites.productIds(user.getAccountId()));
    }
}
