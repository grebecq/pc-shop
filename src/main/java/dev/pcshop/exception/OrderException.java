package dev.pcshop.exception;

/** Ошибка, которую можно показать покупателю как есть. */
public class OrderException extends RuntimeException {

    public OrderException(String message) {
        super(message);
    }
}
