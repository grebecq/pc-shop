package dev.pcshop.controller.web;

import dev.pcshop.config.SecurityConfig;
import dev.pcshop.dto.request.RegisterRequest;
import dev.pcshop.exception.DuplicateResourceException;
import dev.pcshop.exception.MailDeliveryException;
import dev.pcshop.security.ShopUser;
import dev.pcshop.service.AccountService;
import dev.pcshop.service.EmailConfirmService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AccountService accounts;
    private final EmailConfirmService emailConfirm;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    @GetMapping("/login")
    public String login(Authentication auth) {
        return auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof ShopUser ? "redirect:/account" : "account/login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("form", RegisterRequest.empty());
        return "account/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterRequest form, BindingResult errors,
                    @RequestParam(required = false) String next,
                    HttpServletRequest request, HttpServletResponse response) {
        if (errors.hasErrors()) {
            return "account/register";
        }
        ShopUser user;
        try {
            user = accounts.register(form);
        } catch (DuplicateResourceException e) {
            errors.rejectValue("email", "taken", e.getMessage());
            return "account/register";
        }
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        try {
            emailConfirm.send(user.getAccountId());
        } catch (MailDeliveryException e) {
        }
        return SecurityConfig.isSafeNext(next) ? "redirect:" + next : "redirect:/account?welcome";
    }

    @GetMapping("/email/confirm")
    public String confirmEmail(@RequestParam(required = false) String token, Model model, HttpServletResponse response) {
        response.setHeader("Referrer-Policy", "no-referrer");
        model.addAttribute("confirmed", emailConfirm.confirm(token));
        return "account/email-confirm";
    }
}
