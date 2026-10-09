package dev.pcshop.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Ошибки на страницах сайта: покупатель видит обычную страницу 404, без технических подробностей. */
@ControllerAdvice(basePackages = "dev.pcshop.controller.web")
public class WebExceptionHandler {

    /** Чужой или удалённый адрес в кабинете, несуществующий товар, кривая ссылка на заказ. */
    @ExceptionHandler({ResourceNotFoundException.class, AccountException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound() {
        return "error/404";
    }
}
