package dev.pcshop.controller.web;

import dev.pcshop.config.ShopProperties;
import dev.pcshop.security.ShopUser;
import dev.pcshop.service.CatalogService;
import dev.pcshop.service.FavoriteService;
import dev.pcshop.service.StoreInfoService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Set;

// Общие данные страниц
@ControllerAdvice(basePackageClasses = StorefrontAdvice.class)
@RequiredArgsConstructor
public class StorefrontAdvice {

    private final CatalogService catalog;
    private final ShopProperties props;
    private final FavoriteService favorites;
    private final StoreInfoService store;
    @Value("${shop.metrika-id}")
    private final String metrikaId;

    @ModelAttribute
    public void common(Model model, @AuthenticationPrincipal ShopUser user, HttpServletRequest request) {
        model.addAttribute("categories", catalog.categories());
        model.addAttribute("botLink", props.telegram().botLink());
        model.addAttribute("user", user);

        model.addAttribute("favoriteIds", user == null ? Set.of() : favorites.productIds(user.getAccountId()));
        model.addAttribute("store", store.get());

        model.addAttribute("canonicalUrl", props.siteUrl() + request.getRequestURI());

        model.addAttribute("metrikaId", metrikaId.matches("\\d+") ? metrikaId : null);
    }
}
