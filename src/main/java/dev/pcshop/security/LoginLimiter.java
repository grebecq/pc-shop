package dev.pcshop.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Защита от перебора паролей: после 10 неудачных попыток вход в аккаунт блокируется на 15 минут.
 * Счётчик в памяти — для одного экземпляра приложения этого достаточно.
 */
@Component
public class LoginLimiter extends OncePerRequestFilter implements AuthenticationFailureHandler {

    static final int MAX_FAILURES = 10;
    static final Duration LOCK = Duration.ofMinutes(15);
    private static final int MAX_TRACKED = 10_000;

    private static final Map<String, Attempts> ATTEMPTS = new ConcurrentHashMap<>();

    private record Attempts(int failures, Instant since) {
    }

    /** Попытки считаем по email, чтобы перебор одного аккаунта не помогал сменой IP. */
    private static String key(HttpServletRequest request) {
        String email = request.getParameter("email");
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    public static void reset(HttpServletRequest request) {
        ATTEMPTS.remove(key(request));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && "/login".equals(request.getServletPath()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Attempts a = ATTEMPTS.get(key(request));
        if (a != null && a.failures() >= MAX_FAILURES && a.since().plus(LOCK).isAfter(Instant.now())) {
            response.sendRedirect(request.getContextPath() + "/login?locked");
            return;
        }
        chain.doFilter(request, response);
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        if (ATTEMPTS.size() > MAX_TRACKED) {
            // Не даём карте расти бесконечно: убираем истёкшие записи
            Instant border = Instant.now().minus(LOCK);
            ATTEMPTS.values().removeIf(x -> x.since().isBefore(border));
        }
        ATTEMPTS.compute(key(request), (k, a) -> a == null || a.since().plus(LOCK).isBefore(Instant.now())
                ? new Attempts(1, Instant.now())
                : new Attempts(a.failures() + 1, a.since()));
        response.sendRedirect(request.getContextPath() + "/login?error");
    }
}
