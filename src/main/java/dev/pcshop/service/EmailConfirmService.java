package dev.pcshop.service;

import dev.pcshop.config.ShopProperties;
import dev.pcshop.entity.Account;
import dev.pcshop.entity.StoreInfo;
import dev.pcshop.exception.BadRequestException;
import dev.pcshop.exception.ResourceNotFoundException;
import dev.pcshop.repository.AccountRepository;
import dev.pcshop.util.Tokens;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class EmailConfirmService {

    private static final Duration LINK_TTL = Duration.ofDays(3);

    private static final Duration RESEND_PAUSE = Duration.ofMinutes(1);

    private final AccountRepository accounts;
    private final MailService mail;
    private final ShopProperties props;
    private final StoreInfoService store;

    @Transactional
    public void send(long accountId) {
        Account account = accounts.findById(accountId).orElseThrow(() -> new ResourceNotFoundException("Аккаунт не найден"));
        if (account.isEmailConfirmed()) {

            throw new BadRequestException("Почта уже подтверждена");
        }
        Instant now = Instant.now();
        if (account.getEmailTokenSentAt() != null && account.getEmailTokenSentAt().isAfter(now.minus(RESEND_PAUSE))) {
            throw new BadRequestException("Письмо уже отправлено. Новое можно запросить через минуту");
        }
        String token = Tokens.newToken();
        account.newEmailToken(Tokens.sha256(token), now);

        StoreInfo shop = store.get();
        mail.send(account.getEmail(), "Подтвердите почту в магазине «" + shop.getName() + "»", letter(shop, account.getName(), link(token)));
    }

    @Transactional
    public boolean confirm(String token) {
        Instant now = Instant.now();
        return Tokens.hashOf(token).flatMap(accounts::findByEmailTokenHash)
                .filter(a -> a.getEmailTokenSentAt().plus(LINK_TTL).isAfter(now))
                .map(a -> {
                    a.confirmEmail(now);
                    return true;
                })
                .orElse(false);
    }

    private String link(String token) {
        return UriComponentsBuilder.fromUriString(props.baseUrl())
                .path("/email/confirm")
                .queryParam("token", token)
                .toUriString();
    }

    private static String letter(StoreInfo shop, String name, String link) {
        return """
                Здравствуйте, %s!

                Вы зарегистрировались в магазине «%s». Чтобы подтвердить почту, откройте ссылку:
                %s

                Ссылка действует %d дня. Так мы будем уверены, что письма о заказах и ссылка для смены пароля дойдут до вас.

                Если вы не регистрировались, просто проигнорируйте письмо.

                «%s» — %s
                """.formatted(name, shop.getName(), link, LINK_TTL.toDays(), shop.getName(), shop.getTagline());
    }
}
