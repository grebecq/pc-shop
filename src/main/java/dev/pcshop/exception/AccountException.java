package dev.pcshop.exception;

/** Ошибка, которую можно показать пользователю как есть. */
public class AccountException extends RuntimeException {

    public AccountException(String message) {
        super(message);
    }
}
