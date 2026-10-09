package dev.pcshop.controller.web;

import dev.pcshop.dto.request.AddressRequest;
import dev.pcshop.dto.request.ProfileRequest;
import dev.pcshop.exception.AccountException;
import dev.pcshop.security.ShopUser;
import dev.pcshop.service.AccountService;
import dev.pcshop.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Личный кабинет: заказы, профиль, пароль и адреса доставки. Доступен только после входа. */
@Controller
@RequestMapping("/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accounts;
    private final OrderService orders;

    @GetMapping
    public String orders(@AuthenticationPrincipal ShopUser user, @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("account", accounts.get(user.getAccountId()));
        model.addAttribute("orders", orders.forAccount(user.getAccountId(), Math.max(0, page)));
        model.addAttribute("tab", "orders");
        return "account/orders";
    }

    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal ShopUser user, Model model) {
        var account = accounts.get(user.getAccountId());
        model.addAttribute("account", account);
        model.addAttribute("form", new ProfileRequest(account.getName(), account.getPhone()));
        model.addAttribute("tab", "profile");
        return "account/profile";
    }

    @PostMapping("/profile")
    public String saveProfile(@AuthenticationPrincipal ShopUser user, @Valid @ModelAttribute("form") ProfileRequest form,
                       BindingResult errors, Model model, RedirectAttributes flash) {
        if (errors.hasErrors()) {
            model.addAttribute("account", accounts.get(user.getAccountId()));
            model.addAttribute("tab", "profile");
            return "account/profile";
        }
        accounts.updateProfile(user.getAccountId(), form);
        flash.addFlashAttribute("message", "Данные сохранены");
        return "redirect:/account/profile";
    }

    @PostMapping("/password")
    public String changePassword(@AuthenticationPrincipal ShopUser user, @RequestParam String current,
                          @RequestParam String password, RedirectAttributes flash) {
        try {
            accounts.changePassword(user.getAccountId(), current, password);
            flash.addFlashAttribute("message", "Пароль изменён");
        } catch (AccountException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/account/profile";
    }

    @GetMapping("/addresses")
    public String addresses(@AuthenticationPrincipal ShopUser user, Model model) {
        model.addAttribute("addresses", accounts.addresses(user.getAccountId()));
        model.addAttribute("form", AddressRequest.empty());
        model.addAttribute("tab", "addresses");
        return "account/addresses";
    }

    @PostMapping("/addresses")
    public String addAddress(@AuthenticationPrincipal ShopUser user, @Valid @ModelAttribute("form") AddressRequest form,
                      BindingResult errors, Model model, RedirectAttributes flash) {
        if (!errors.hasErrors()) {
            try {
                accounts.addAddress(user.getAccountId(), form);
                flash.addFlashAttribute("message", "Адрес добавлен");
                return "redirect:/account/addresses";
            } catch (AccountException e) {
                model.addAttribute("error", e.getMessage());
            }
        }
        model.addAttribute("addresses", accounts.addresses(user.getAccountId()));
        model.addAttribute("tab", "addresses");
        return "account/addresses";
    }

    @GetMapping("/addresses/{id}")
    public String editAddress(@AuthenticationPrincipal ShopUser user, @PathVariable long id, Model model) {
        model.addAttribute("address", accounts.address(user.getAccountId(), id));
        model.addAttribute("form", accounts.address(user.getAccountId(), id).toRequest());
        model.addAttribute("tab", "addresses");
        return "account/address-edit";
    }

    @PostMapping("/addresses/{id}")
    public String updateAddress(@AuthenticationPrincipal ShopUser user, @PathVariable long id,
                         @Valid @ModelAttribute("form") AddressRequest form, BindingResult errors,
                         Model model, RedirectAttributes flash) {
        if (errors.hasErrors()) {
            model.addAttribute("address", accounts.address(user.getAccountId(), id));
            model.addAttribute("tab", "addresses");
            return "account/address-edit";
        }
        accounts.updateAddress(user.getAccountId(), id, form);
        flash.addFlashAttribute("message", "Адрес сохранён");
        return "redirect:/account/addresses";
    }

    @PostMapping("/addresses/{id}/default")
    public String makeDefault(@AuthenticationPrincipal ShopUser user, @PathVariable long id, RedirectAttributes flash) {
        accounts.makeDefault(user.getAccountId(), id);
        flash.addFlashAttribute("message", "Основной адрес изменён");
        return "redirect:/account/addresses";
    }

    @PostMapping("/addresses/{id}/delete")
    public String deleteAddress(@AuthenticationPrincipal ShopUser user, @PathVariable long id, RedirectAttributes flash) {
        accounts.deleteAddress(user.getAccountId(), id);
        flash.addFlashAttribute("message", "Адрес удалён");
        return "redirect:/account/addresses";
    }
}
