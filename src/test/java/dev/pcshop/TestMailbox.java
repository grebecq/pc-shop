package dev.pcshop;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Почта в тестах: письма собираются как настоящие (MIME, UTF-8), но не уходят на SMTP, а складываются в список.
 * {@link #setDown} имитирует недоступный почтовый сервер.
 */
public class TestMailbox extends JavaMailSenderImpl {

    public record Letter(String to, String from, String subject, String text) {
    }

    private final List<Letter> letters = new CopyOnWriteArrayList<>();
    private volatile boolean down;

    public TestMailbox() {
        // Как у отправителя, которого настраивает Spring Boot (spring.mail.default-encoding)
        setDefaultEncoding("UTF-8");
    }

    @Override
    protected void doSend(MimeMessage[] messages, Object[] originals) {
        if (down) {
            throw new MailSendException("Почтовый сервер недоступен");
        }
        try {
            for (MimeMessage m : messages) {
                var from = (InternetAddress) m.getFrom()[0];
                letters.add(new Letter(InternetAddress.toUnicodeString(m.getRecipients(Message.RecipientType.TO)),
                        from.getPersonal() + " <" + from.getAddress() + ">", m.getSubject(), (String) m.getContent()));
            }
        } catch (MessagingException | IOException e) {
            throw new MailPreparationException(e);
        }
    }

    /** Письма на этот адрес, старые первыми. */
    public List<Letter> to(String email) {
        return letters.stream().filter(l -> l.to().equalsIgnoreCase(email)).toList();
    }

    public void setDown(boolean down) {
        this.down = down;
    }
}
