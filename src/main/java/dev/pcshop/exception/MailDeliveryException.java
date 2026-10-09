package dev.pcshop.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Письмо не ушло: почтовый сервер недоступен или отказал. Сообщение можно показать как есть. */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class MailDeliveryException extends RuntimeException {

    public MailDeliveryException(String message) {
        super(message);
    }

    /** cause — настоящая причина для админа (страница настроек), покупателю показываем только message. */
    public MailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
