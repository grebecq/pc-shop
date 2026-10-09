package dev.pcshop.controller.web;

import dev.pcshop.dto.request.PasswordForgotRequest;
import dev.pcshop.dto.request.PasswordResetRequest;
import dev.pcshop.exception.BadRequestException;
import dev.pcshop.exception.MailDeliveryException;
import dev.pcshop.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** «Забыли пароль?» и форма нового пароля по ссылке из письма. Доступны без входа. */
@Controller
@RequestMapping("/password")
@RequiredArgsConstructor
public class PasswordController {

    private final PasswordResetService resets;

    @GetMapping("/forgot")
    public String forgotForm(Model model) {
        model.addAttribute("form", PasswordForgotRequest.empty());
        return "account/password-forgot";
    }

    /** Ответ одинаковый, есть такой аккаунт или нет: по нему нельзя узнать, зарегистрирован ли адрес. */
    @PostMapping("/forgot")
    public String forgot(@Valid @ModelAttribute("form") PasswordForgotRequest form, BindingResult errors, Model model) {
        if (errors.hasErrors()) {
            return "account/password-forgot";
        }
        try {
            resets.sendLinkByEmail(form.email());
        } catch (MailDeliveryException e) {
            model.addAttribute("error", e.getMessage());
            return "account/password-forgot";
        }
        return "redirect:/password/forgot?sent";
    }

    @GetMapping("/reset")
    public String resetForm(@RequestParam(required = false) String token, Model model, HttpServletResponse response) {
        // Токен из адреса страницы не должен уходить на другие сайты в заголовке Referer
        response.setHeader("Referrer-Policy", "no-referrer");
        if (!resets.isValid(token)) {
            model.addAttribute("expired", true);
            return "account/password-reset";
        }
        model.addAttribute("form", PasswordResetRequest.forToken(token));
        return "account/password-reset";
    }

    @PostMapping("/reset")
    public String reset(@Valid @ModelAttribute("form") PasswordResetRequest form, BindingResult errors, Model model,
                        Authentication auth, HttpServletRequest request, HttpServletResponse response) {
        if (errors.hasErrors()) {
            return "account/password-reset";
        }
        try {
            resets.resetPassword(form.token(), form.password());
        } catch (BadRequestException e) {
            model.addAttribute("expired", true);
            return "account/password-reset";
        }
        // Если в этом браузере кто-то вошёл, выходим: дальше вход только с новым паролем
        if (auth != null) {
            new SecurityContextLogoutHandler().logout(request, response, auth);
        }
        return "redirect:/login?reset";
    }
}
