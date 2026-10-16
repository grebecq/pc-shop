package dev.pcshop.config;

import dev.pcshop.security.LoginLimiter;
import dev.pcshop.service.AccountService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
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

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
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
                        .requestMatchers("/account/**", "/configurator/save", "/p/*/reviews", "/p/*/stock-alert").authenticated()

                        .requestMatchers("/password/**").permitAll()
                        .anyRequest().permitAll())
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("email")
                        .successHandler(afterLogin())
                        .failureHandler(loginLimiter)
                        .permitAll())
                .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/"))

                .csrf(csrf -> csrf.csrfTokenRepository(new CookieCsrfTokenRepository())
                        .ignoringRequestMatchers("/api/payments/yookassa"))
                .addFilterBefore(loginLimiter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

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
