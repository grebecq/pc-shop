package dev.pcshop.service;

import dev.pcshop.exception.MailDeliveryException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** Письма покупателям: простой текст через SMTP из настроек spring.mail.*. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender sender;
    @Value("${shop.mail.from}")
    private final String from;

    public void send(String to, String subject, String text) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            sender.send(message);
        } catch (MailException e) {
            // Только причина, без адреса и текста: в письмах бывают ссылки, по которым можно сменить пароль
            log.warn("Не удалось отправить письмо. Тема: {}. Причина: {}", subject, e.getMessage());
            throw new MailDeliveryException("Не удалось отправить письмо, попробуйте позже", e);
        }
    }
}
