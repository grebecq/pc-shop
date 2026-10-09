package dev.pcshop.controller.web;

import dev.pcshop.dto.request.ProductSearchRequest;
import dev.pcshop.entity.Category;
import dev.pcshop.entity.Product;
import dev.pcshop.enums.Slot;
import dev.pcshop.exception.OrderException;
import dev.pcshop.model.Build;
import dev.pcshop.security.ShopUser;
import dev.pcshop.service.AccountService;
import dev.pcshop.service.CatalogService;
import dev.pcshop.service.DeliveryService;
import dev.pcshop.service.OrderService;
import dev.pcshop.util.Attributes;
import dev.pcshop.util.CatalogQueries;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Страницы сайта-витрины. Корзина хранится в браузере, заказ создаётся через /api/orders. */
@Controller
@RequiredArgsConstructor
public class ShopController {

    private final CatalogService catalog;
    private final OrderService orders;
    private final AccountService accounts;
    private final DeliveryService delivery;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("featured", catalog.featured());
        return "shop/home";
    }

    @GetMapping("/c/{slug}")
    public String category(@PathVariable String slug, @RequestParam MultiValueMap<String, String> params, Model model) {
        Category category = catalog.category(slug);
        ProductSearchRequest query = CatalogQueries.fromParams(slug, params);
        var page = catalog.search(query);

        model.addAttribute("category", category);
        model.addAttribute("facets", catalog.facets(slug));
        model.addAttribute("page", page);
        model.addAttribute("query", query);
        model.addAttribute("selected", query.filters());
        model.addAttribute("sort", params.getFirst("sort") == null ? "popular" : params.getFirst("sort"));
        model.addAttribute("pageLinks", pageLinks(slug, params, page.getTotalPages()));
        return "shop/category";
    }

    @GetMapping("/p/{slug}")
    public String product(@PathVariable String slug, Model model) {
        Product product = catalog.product(slug);
        model.addAttribute("product", product);
        model.addAttribute("specs", Attributes.readable(product));
        // «Собрать ПК с этим товаром»: товар уже в сборке, дальше — первый пустой шаг конфигуратора
        model.addAttribute("buildLink", Slot.byCategory(product.getCategory().getSlug()).map(slot -> {
            Build build = Build.empty().with(slot, product);
            return build.firstEmpty().map(build::pickLink).orElseGet(build::link);
        }).orElse(null));
        return "shop/product";
    }

    @GetMapping("/search")
    public String search(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page, Model model) {
        String query = q.strip();
        model.addAttribute("q", query);
        if (!query.isEmpty()) {
            var result = catalog.searchText(query, Math.max(0, page));
            model.addAttribute("page", result);
            model.addAttribute("pageLinks", java.util.stream.IntStream.range(0, result.getTotalPages())
                    .mapToObj(i -> "/search?q=" + enc(query) + "&page=" + i).toList());
        }
        return "shop/search";
    }

    @GetMapping("/cart")
    public String cart(@AuthenticationPrincipal ShopUser user, Model model) {
        // Вошедшему покупателю подставляем имя, телефон и сохранённые адреса
        if (user != null) {
            model.addAttribute("account", accounts.get(user.getAccountId()));
            model.addAttribute("addresses", accounts.addresses(user.getAccountId()));
        }
        model.addAttribute("pickupPoints", delivery.pickupPoints());
        model.addAttribute("courierPrice", delivery.courierPrice());
        model.addAttribute("freeFrom", delivery.freeFrom());
        return "shop/cart";
    }

    @GetMapping("/order/{publicId}")
    public String order(@PathVariable UUID publicId, @AuthenticationPrincipal ShopUser user, Model model) {
        var order = orders.byPublicId(publicId);
        model.addAttribute("order", order);
        // Кнопку отмены показываем тому, кто может отменить: см. OrderService.cancelByCustomer
        model.addAttribute("canCancel", order.cancellableByCustomer()
                && (order.getAccountId() == null || user != null && order.getAccountId().equals(user.getAccountId())));
        return "shop/order";
    }

    @PostMapping("/order/{publicId}/cancel")
    public String cancelOrder(@PathVariable UUID publicId, @AuthenticationPrincipal ShopUser user, RedirectAttributes flash) {
        try {
            orders.cancelByCustomer(publicId, user == null ? null : user.getAccountId());
            flash.addFlashAttribute("message", "Заказ отменён");
        } catch (OrderException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/order/" + publicId;
    }

    /** Ссылки на страницы с сохранением фильтров. */
    private static List<String> pageLinks(String slug, MultiValueMap<String, String> params, int totalPages) {
        String base = params.entrySet().stream()
                .filter(e -> !e.getKey().equals("page"))
                .flatMap(e -> e.getValue().stream().map(v -> enc(e.getKey()) + "=" + enc(v)))
                .collect(Collectors.joining("&"));
        return java.util.stream.IntStream.range(0, totalPages)
                .mapToObj(i -> "/c/" + slug + "?" + (base.isEmpty() ? "" : base + "&") + "page=" + i)
                .toList();
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
