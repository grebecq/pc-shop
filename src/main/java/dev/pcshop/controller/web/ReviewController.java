package dev.pcshop.controller.web;

import dev.pcshop.dto.request.ReviewRequest;
import dev.pcshop.exception.BadRequestException;
import dev.pcshop.exception.DuplicateResourceException;
import dev.pcshop.security.ShopUser;
import dev.pcshop.service.CatalogService;
import dev.pcshop.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class ReviewController {

    private final CatalogService catalog;
    private final ReviewService reviews;

    @PostMapping("/p/{slug}/reviews")
    public String add(@PathVariable String slug, @Valid @ModelAttribute("review") ReviewRequest form, BindingResult errors,
                      @AuthenticationPrincipal ShopUser user, RedirectAttributes flash) {
        var product = catalog.product(slug);
        String back = "redirect:/p/" + slug + "#reviews";
        if (errors.hasErrors()) {
            flash.addFlashAttribute("reviewError", errors.getAllErrors().getFirst().getDefaultMessage());
            flash.addFlashAttribute("review", form);
            return back;
        }
        try {
            reviews.add(user.getAccountId(), product.getId(), form);
            flash.addFlashAttribute("reviewMessage", "Спасибо! Отзыв опубликован");
        } catch (BadRequestException | DuplicateResourceException e) {
            flash.addFlashAttribute("reviewError", e.getMessage());
        }
        return back;
    }
}
