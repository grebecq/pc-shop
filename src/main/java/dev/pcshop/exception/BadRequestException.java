package dev.pcshop.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Нарушено правило магазина (лимит адресов, устаревшая ссылка для смены пароля и т.п.). Сообщение можно показать как есть. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
