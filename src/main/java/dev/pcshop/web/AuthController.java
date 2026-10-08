package dev.pcshop.web;

import dev.pcshop.account.AccountException;
import dev.pcshop.account.AccountService;
import dev.pcshop.account.RegisterForm;
import dev.pcshop.account.ShopUser;
import dev.pcshop.config.SecurityConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
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

/** Вход и регистрация покупателей. Сам вход обрабатывает Spring Security (POST /login). */
@Controller
class AuthController {

    private final AccountService accounts;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    AuthController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/login")
    String login(Authentication auth) {
        return auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof ShopUser ? "redirect:/account" : "account/login";
    }

    @GetMapping("/register")
    String registerForm(Model model) {
        model.addAttribute("form", RegisterForm.empty());
        return "account/register";
    }

    @PostMapping("/register")
    String register(@Valid @ModelAttribute("form") RegisterForm form, BindingResult errors,
                    @RequestParam(required = false) String next,
                    HttpServletRequest request, HttpServletResponse response) {
        if (errors.hasErrors()) {
            return "account/register";
        }
        ShopUser user;
        try {
            user = accounts.register(form);
        } catch (AccountException e) {
            errors.rejectValue("email", "taken", e.getMessage());
            return "account/register";
        }
        // Сразу входим под новым аккаунтом: новая сессия против фиксации, контекст сохраняем в сессию
        request.changeSessionId();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        return SecurityConfig.isSafeNext(next) ? "redirect:" + next : "redirect:/account?welcome";
    }
}
