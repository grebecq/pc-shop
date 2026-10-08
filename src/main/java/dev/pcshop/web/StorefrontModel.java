package dev.pcshop.web;

import dev.pcshop.catalog.CatalogService;
import dev.pcshop.config.ShopProperties;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Данные шапки сайта (категории, ссылка на бота) для всех страниц витрины. */
@ControllerAdvice(assignableTypes = {ShopController.class, PaymentController.class})
class StorefrontModel {

    private final CatalogService catalog;
    private final ShopProperties props;

    StorefrontModel(CatalogService catalog, ShopProperties props) {
        this.catalog = catalog;
        this.props = props;
    }

    @ModelAttribute
    void common(Model model) {
        model.addAttribute("categories", catalog.categories());
        model.addAttribute("botLink", props.telegram().botLink());
    }
}
