package dev.pcshop.web;

import dev.pcshop.account.AccountException;
import dev.pcshop.account.ShopUser;
import dev.pcshop.catalog.CatalogService;
import dev.pcshop.config.ShopProperties;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Общие данные шапки (категории, ссылка на бота, вошедший покупатель) для всех страниц сайта. */
@ControllerAdvice(basePackageClasses = StorefrontModel.class)
class StorefrontModel {

    private final CatalogService catalog;
    private final ShopProperties props;

    StorefrontModel(CatalogService catalog, ShopProperties props) {
        this.catalog = catalog;
        this.props = props;
    }

    @ModelAttribute
    void common(Model model, @AuthenticationPrincipal ShopUser user) {
        model.addAttribute("categories", catalog.categories());
        model.addAttribute("botLink", props.telegram().botLink());
        model.addAttribute("user", user);
    }

    /** Чужой или удалённый адрес в кабинете, кривая ссылка на заказ — обычная 404, без подробностей. */
    @ExceptionHandler({AccountException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String notFound() {
        return "error/404";
    }
}
