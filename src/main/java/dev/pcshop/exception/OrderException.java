package dev.pcshop.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Заказ нельзя создать или изменить: товара не хватает, статус уже финальный. Сообщение можно показать как есть. */
@ResponseStatus(HttpStatus.CONFLICT)
public class OrderException extends RuntimeException {

    public OrderException(String message) {
        super(message);
    }
}
