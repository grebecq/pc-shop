package dev.pcshop.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Контрольная цифра номера карты по алгоритму Луна: ловит опечатку в одной цифре и перестановку соседних.
 * Проверяет только номер правильного формата (16–19 цифр): пустое поле и неверный формат ловят
 * {@code @NotBlank} и {@code @Pattern} рядом, чтобы у поля было одно понятное сообщение, а не два-три сразу.
 */
@Documented
@Constraint(validatedBy = Luhn.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Luhn {

    String message() default "Неверный номер карты";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<Luhn, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null || !value.matches("\\d{16,19}")) {
                return true;
            }
            return checksum(value) % 10 == 0;
        }

        /** Справа налево каждую вторую цифру удваиваем (из двузначного результата вычитаем 9) и всё складываем. */
        static int checksum(String digits) {
            int sum = 0;
            boolean twice = false;
            for (int i = digits.length() - 1; i >= 0; i--) {
                int d = digits.charAt(i) - '0';
                if (twice) {
                    d *= 2;
                    if (d > 9) {
                        d -= 9;
                    }
                }
                sum += d;
                twice = !twice;
            }
            return sum;
        }
    }
}
