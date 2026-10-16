package dev.pcshop.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CardPaymentRequestTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private static final DateTimeFormatter MM_YY = DateTimeFormatter.ofPattern("MM/yy");

    @Test
    void spacesInNumberAreRemoved() {
        var card = new CardPaymentRequest(" 4242 4242  4242 4242 ", " 12 / 40 ", " 123 ", " IVAN ");
        assertThat(card.number()).isEqualTo("4242424242424242");
        assertThat(card.expiry()).isEqualTo("12/40");
        assertThat(card.cvc()).isEqualTo("123");
        assertThat(card.holder()).isEqualTo("IVAN");
        assertThat(errors(card)).isEmpty();
    }

    @Test
    void numberChecks() {
        assertThat(errors(card("4242 4242 4242 4241", "12/40"))).containsExactly("Неверный номер карты: проверьте цифры");
        assertThat(errors(card("4242 4242 4242 424", "12/40"))).containsExactly("Номер карты — от 16 до 19 цифр");
        assertThat(errors(card("4242-4242-4242-4242", "12/40"))).containsExactly("Номер карты — от 16 до 19 цифр");
        assertThat(errors(card("6011 0000 0000 0000 001", "12/40"))).isEmpty();
    }

    @Test
    void expiryChecks() {
        YearMonth now = YearMonth.now(ZoneId.of("Europe/Moscow"));
        assertThat(errors(card("4242424242424242", now.format(MM_YY)))).as("текущий месяц ещё действует").isEmpty();
        assertThat(errors(card("4242424242424242", now.minusMonths(1).format(MM_YY)))).containsExactly("Срок действия карты истёк");
        assertThat(errors(card("4242424242424242", "13/40"))).containsExactly("Срок — в формате ММ/ГГ, месяц от 01 до 12");
        assertThat(errors(card("4242424242424242", "1240"))).containsExactly("Срок — в формате ММ/ГГ, месяц от 01 до 12");
    }

    @Test
    void toStringHidesNumberAndCvc() {
        String text = new CardPaymentRequest("4242424242424242", "12/40", "987", "").toString();
        assertThat(text).contains("4242").doesNotContain("4242424242424242", "987");
    }

    private static CardPaymentRequest card(String number, String expiry) {
        return new CardPaymentRequest(number, expiry, "123", "");
    }

    private static List<String> errors(CardPaymentRequest card) {
        return VALIDATOR.validate(card).stream().map(ConstraintViolation::getMessage).toList();
    }
}
