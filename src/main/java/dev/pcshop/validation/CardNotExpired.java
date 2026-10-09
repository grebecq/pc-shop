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
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.regex.Pattern;

/**
 * Срок действия карты «ММ/ГГ» не в прошлом. Карта действует до конца указанного месяца,
 * поэтому текущий месяц ещё подходит. Формат проверяет {@code @Pattern} рядом, здесь — только дата.
 */
@Documented
@Constraint(validatedBy = CardNotExpired.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface CardNotExpired {

    String message() default "Срок действия карты истёк";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<CardNotExpired, String> {

        private static final Pattern MM_YY = Pattern.compile("(0[1-9]|1[0-2])/(\\d{2})");
        private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            var m = value == null ? null : MM_YY.matcher(value);
            if (m == null || !m.matches()) {
                return true;
            }
            YearMonth expiry = YearMonth.of(2000 + Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
            YearMonth now = YearMonth.now(context.getClockProvider().getClock().withZone(ZONE));
            return !expiry.isBefore(now);
        }
    }
}
