package dev.pcshop.config;

import dev.pcshop.security.LoginLimiter;
import dev.pcshop.service.AccountService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Две независимые зоны входа:
 * админка — логин и пароль владельца из переменных окружения (HTTP Basic),
 * сайт — аккаунты покупателей из базы (форма входа).
 */
@Configuration
public class SecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain adminChain(HttpSecurity http, ShopProperties props, PasswordEncoder encoder) throws Exception {
        UserDetailsService admin = new InMemoryUserDetailsManager(User.withUsername(props.admin().username())
                .password(encoder.encode(props.admin().password()))
                .roles("ADMIN")
                .build());
        http
                .securityMatcher("/admin/**")
                .authenticationManager(manager(admin, encoder))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("ADMIN"))
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain shopChain(HttpSecurity http, AccountService accounts, PasswordEncoder encoder,
                                  LoginLimiter loginLimiter) throws Exception {
        http
                .authenticationManager(manager(accounts, encoder))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/account/**", "/configurator/save").authenticated()
                        .anyRequest().permitAll())
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("email")
                        .successHandler(afterLogin())
                        .failureHandler(loginLimiter)
                        .permitAll())
                .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/"))
                // CSRF включён везде, в том числе для /api: JS сайта берёт токен и имя заголовка из meta-тегов страницы.
                // Токен храним в cookie, а не в сессии: иначе каждый анонимный посетитель (и поисковый бот)
                // получал бы сессию в памяти на 14 дней
                .csrf(csrf -> csrf.csrfTokenRepository(new CookieCsrfTokenRepository()))
                .addFilterBefore(loginLimiter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * После входа: на страницу из параметра next (только внутренние пути вида /cart — без открытого редиректа),
     * иначе туда, куда покупатель шёл до входа, иначе в кабинет.
     */
    private static AuthenticationSuccessHandler afterLogin() {
        var fallback = new SavedRequestAwareAuthenticationSuccessHandler();
        fallback.setDefaultTargetUrl("/account");
        return (request, response, authentication) -> {
            LoginLimiter.reset(request);
            String next = request.getParameter("next");
            if (isSafeNext(next)) {
                response.sendRedirect(next);
            } else {
                fallback.onAuthenticationSuccess(request, response, authentication);
            }
        };
    }

    /**
     * Внутренний путь сайта: начинается с одного «/», без хоста. Параметры — только из букв, цифр, «=» и «&»
     * (нужно, чтобы после входа вернуться к своей сборке в конфигураторе: /configurator?cpu=1&mb=5).
     */
    public static boolean isSafeNext(String next) {
        return next != null && next.matches("^/(?!/)[a-z0-9/-]*(\\?[a-z0-9=&]*)?$");
    }

    private static ProviderManager manager(UserDetailsService users, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
