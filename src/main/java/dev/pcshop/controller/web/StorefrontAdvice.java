package dev.pcshop.controller.web;

import dev.pcshop.config.ShopProperties;
import dev.pcshop.security.ShopUser;
import dev.pcshop.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Общие данные шапки (категории, ссылка на бота, вошедший покупатель) для всех страниц сайта. */
@ControllerAdvice(basePackageClasses = StorefrontAdvice.class)
@RequiredArgsConstructor
public class StorefrontAdvice {

    private final CatalogService catalog;
    private final ShopProperties props;

    @ModelAttribute
    public void common(Model model, @AuthenticationPrincipal ShopUser user) {
        model.addAttribute("categories", catalog.categories());
        model.addAttribute("botLink", props.telegram().botLink());
        model.addAttribute("user", user);
    }
}
