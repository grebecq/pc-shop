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

/**
 * Общие данные всех страниц сайта: категории и ссылка на бота в шапке, вошедший покупатель и его избранное,
 * контакты и реквизиты для подвала, адрес страницы для поисковиков и счётчик Метрики.
 */
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
        // Чтобы в карточках товаров сразу были закрашены сердечки избранного
        model.addAttribute("favoriteIds", user == null ? Set.of() : favorites.productIds(user.getAccountId()));
        model.addAttribute("store", store.get());
        // Без параметров: страница каталога с фильтрами и сортировкой для поисковика — та же страница категории
        model.addAttribute("canonicalUrl", props.siteUrl() + request.getRequestURI());
        // Номер попадает в скрипт страницы, поэтому берём только цифры
        model.addAttribute("metrikaId", metrikaId.matches("\\d+") ? metrikaId : null);
    }
}
