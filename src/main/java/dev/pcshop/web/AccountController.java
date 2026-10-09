package dev.pcshop.web;

import dev.pcshop.account.AccountException;
import dev.pcshop.account.AccountService;
import dev.pcshop.account.AddressForm;
import dev.pcshop.account.ProfileForm;
import dev.pcshop.account.ShopUser;
import dev.pcshop.order.OrderService;
import jakarta.validation.Valid;
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
class AccountController {

    private final AccountService accounts;
    private final OrderService orders;

    AccountController(AccountService accounts, OrderService orders) {
        this.accounts = accounts;
        this.orders = orders;
    }

    @GetMapping
    String orders(@AuthenticationPrincipal ShopUser user, @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("account", accounts.get(user.getAccountId()));
        model.addAttribute("orders", orders.forAccount(user.getAccountId(), Math.max(0, page)));
        model.addAttribute("tab", "orders");
        return "account/orders";
    }

    @GetMapping("/profile")
    String profile(@AuthenticationPrincipal ShopUser user, Model model) {
        var account = accounts.get(user.getAccountId());
        model.addAttribute("account", account);
        model.addAttribute("form", new ProfileForm(account.getName(), account.getPhone()));
        model.addAttribute("tab", "profile");
        return "account/profile";
    }

    @PostMapping("/profile")
    String saveProfile(@AuthenticationPrincipal ShopUser user, @Valid @ModelAttribute("form") ProfileForm form,
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
    String changePassword(@AuthenticationPrincipal ShopUser user, @RequestParam String current,
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
    String addresses(@AuthenticationPrincipal ShopUser user, Model model) {
        model.addAttribute("addresses", accounts.addresses(user.getAccountId()));
        model.addAttribute("form", AddressForm.empty());
        model.addAttribute("tab", "addresses");
        return "account/addresses";
    }

    @PostMapping("/addresses")
    String addAddress(@AuthenticationPrincipal ShopUser user, @Valid @ModelAttribute("form") AddressForm form,
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
    String editAddress(@AuthenticationPrincipal ShopUser user, @PathVariable long id, Model model) {
        model.addAttribute("address", accounts.address(user.getAccountId(), id));
        model.addAttribute("form", accounts.address(user.getAccountId(), id).toForm());
        model.addAttribute("tab", "addresses");
        return "account/address-edit";
    }

    @PostMapping("/addresses/{id}")
    String updateAddress(@AuthenticationPrincipal ShopUser user, @PathVariable long id,
                         @Valid @ModelAttribute("form") AddressForm form, BindingResult errors,
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
    String makeDefault(@AuthenticationPrincipal ShopUser user, @PathVariable long id, RedirectAttributes flash) {
        accounts.makeDefault(user.getAccountId(), id);
        flash.addFlashAttribute("message", "Основной адрес изменён");
        return "redirect:/account/addresses";
    }

    @PostMapping("/addresses/{id}/delete")
    String deleteAddress(@AuthenticationPrincipal ShopUser user, @PathVariable long id, RedirectAttributes flash) {
        accounts.deleteAddress(user.getAccountId(), id);
        flash.addFlashAttribute("message", "Адрес удалён");
        return "redirect:/account/addresses";
    }
}
