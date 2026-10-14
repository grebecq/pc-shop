package dev.pcshop.service;

import dev.pcshop.config.ShopProperties;
import dev.pcshop.entity.Account;
import dev.pcshop.entity.PasswordResetToken;
import dev.pcshop.exception.BadRequestException;
import dev.pcshop.exception.ResourceNotFoundException;
import dev.pcshop.repository.AccountRepository;
import dev.pcshop.repository.PasswordResetTokenRepository;
import dev.pcshop.util.Tokens;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;

/**
 * Смена пароля по ссылке из письма. В ссылке случайный токен, в базе только его SHA-256.
 * Ссылка действует 30 минут и срабатывает один раз, новая ссылка отменяет прежние.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final String LINK_EXPIRED = "Ссылка устарела, запросите новую";
    private static final Duration LINK_TTL = Duration.ofMinutes(30);
    // Не чаще одного письма в минуту на аккаунт, чтобы чужой человек не завалил ящик письмами
    private static final Duration RESEND_PAUSE = Duration.ofMinutes(1);
    private static final String SUBJECT = "Смена пароля в магазине «Сборка»";

    private final AccountRepository accounts;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final MailService mail;
    private final ShopProperties props;

    /** «Забыли пароль?»: письмо уходит, только если аккаунт с таким email есть. Снаружи разницы не видно. */
    @Transactional
    public void sendLinkByEmail(String email) {
        accounts.findByEmail(AccountService.normalize(email)).ifPresent(this::sendLink);
    }

    /** Кнопка «Отправить письмо» в профиле. */
    @Transactional
    public void sendLinkToAccount(long accountId) {
        sendLink(accounts.findById(accountId).orElseThrow(() -> new ResourceNotFoundException("Аккаунт не найден")));
    }

    /** Ссылка ещё действует, можно показывать форму нового пароля. */
    @Transactional(readOnly = true)
    public boolean isValid(String token) {
        return Tokens.hashOf(token).flatMap(tokens::findByTokenHash).filter(t -> t.isActive(Instant.now())).isPresent();
    }

    /** Меняет пароль и гасит ссылку. Просроченная, использованная или выдуманная ссылка — BadRequestException. */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        Instant now = Instant.now();
        PasswordResetToken link = Tokens.hashOf(token).flatMap(tokens::findForUpdateByTokenHash)
                .filter(t -> t.isActive(now))
                .orElseThrow(() -> new BadRequestException(LINK_EXPIRED));
        Account account = accounts.findById(link.getAccountId()).orElseThrow(() -> new BadRequestException(LINK_EXPIRED));
        account.setPasswordHash(encoder.encode(newPassword));
        // Ссылка пришла на эту почту, значит почта настоящая
        account.confirmEmail(now);
        link.markUsed(now);
    }

    private void sendLink(Account account) {
        if (tokens.existsByAccountIdAndCreatedAtAfter(account.getId(), Instant.now().minus(RESEND_PAUSE))) {
            return;
        }
        // Действует только последняя ссылка: прежние, в том числе ещё не просроченные, удаляем
        tokens.deleteAllByAccountId(account.getId());
        String token = Tokens.newToken();
        tokens.save(new PasswordResetToken(account.getId(), Tokens.sha256(token), Instant.now().plus(LINK_TTL)));
        // Если письмо не ушло, исключение откатит транзакцию: прежние ссылки останутся, а повторить можно сразу
        mail.send(account.getEmail(), SUBJECT, letter(account.getName(), link(token)));
    }

    private String link(String token) {
        return UriComponentsBuilder.fromUriString(props.baseUrl())
                .path("/password/reset")
                .queryParam("token", token)
                .toUriString();
    }

    private static String letter(String name, String link) {
        return """
                Здравствуйте, %s!

                Вы попросили сменить пароль в магазине «Сборка». Чтобы задать новый пароль, откройте ссылку:
                %s

                Ссылка действует %d минут и сработает только один раз.

                Если вы не запрашивали смену пароля, просто проигнорируйте письмо: пароль останется прежним.

                «Сборка» — комплектующие и сборка ПК
                """.formatted(name, link, LINK_TTL.toMinutes());
    }
}
