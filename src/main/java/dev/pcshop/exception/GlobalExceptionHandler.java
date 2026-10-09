package dev.pcshop.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;

/** Ошибки REST API в одном формате. Страницы сайта обрабатывает {@link WebExceptionHandler}. */
@Slf4j
@RestControllerAdvice(basePackages = "dev.pcshop.controller.api")
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleNotFound(ResourceNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
    }

    @ExceptionHandler(OrderException.class)
    public ResponseEntity<ErrorResponseDto> handleOrder(OrderException e) {
        return build(HttpStatus.CONFLICT, "Conflict", e.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponseDto> handleBadRequest(BadRequestException e) {
        return build(HttpStatus.BAD_REQUEST, "Bad request", e.getMessage());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponseDto> handleDuplicate(DuplicateResourceException e) {
        return build(HttpStatus.CONFLICT, "Conflict", e.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDto> handleDataIntegrity(DataIntegrityViolationException e) {
        log.debug("Нарушено ограничение БД", e);
        return build(HttpStatus.CONFLICT, "Conflict", "Запись уже существует или конфликтует с другими данными");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getDefaultMessage())
                .orElse("Некорректные данные запроса");
        return build(HttpStatus.BAD_REQUEST, "Validation failed", message);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponseDto> handleMethodValidation(HandlerMethodValidationException e) {
        String message = e.getAllErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Некорректные параметры запроса");
        return build(HttpStatus.BAD_REQUEST, "Validation failed", message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponseDto> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(ConstraintViolation::getMessage)
                .orElse("Некорректные параметры запроса");
        return build(HttpStatus.BAD_REQUEST, "Validation failed", message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDto> handleUnreadable(HttpMessageNotReadableException e) {
        log.debug("Не удалось разобрать тело запроса", e);
        return build(HttpStatus.BAD_REQUEST, "Bad request", "Некорректное тело запроса");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDto> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return build(HttpStatus.BAD_REQUEST, "Bad request", "Параметр '" + e.getName() + "' имеет недопустимое значение");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponseDto> handleMissingParam(MissingServletRequestParameterException e) {
        return build(HttpStatus.BAD_REQUEST, "Bad request", "Не передан обязательный параметр '" + e.getParameterName() + "'");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGeneral(Exception e) {
        log.error("Необработанная ошибка", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "Внутренняя ошибка сервера");
    }

    private ResponseEntity<ErrorResponseDto> build(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(new ErrorResponseDto(error, message, LocalDateTime.now()));
    }
}
