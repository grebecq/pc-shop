package dev.pcshop.dto.request;

import dev.pcshop.util.Strings;
import dev.pcshop.validation.CardNotExpired;
import dev.pcshop.validation.Luhn;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Данные карты из формы тестовой оплаты. Живут только в запросе: в заказ попадают
 * платёжная система и последние 4 цифры, номер целиком и CVC нигде не сохраняются.
 */
public record CardPaymentRequest(

        @NotBlank(message = "Введите номер карты")
        @Pattern(regexp = "^$|^\\d{16,19}$", message = "Номер карты — от 16 до 19 цифр")
        @Luhn(message = "Неверный номер карты: проверьте цифры")
        String number,

        @NotBlank(message = "Укажите срок действия карты")
        @Pattern(regexp = "^$|^(0[1-9]|1[0-2])/\\d{2}$", message = "Срок — в формате ММ/ГГ, месяц от 01 до 12")
        @CardNotExpired(message = "Срок действия карты истёк")
        String expiry,

        @NotBlank(message = "Введите CVC")
        @Pattern(regexp = "^$|^\\d{3}$", message = "CVC — 3 цифры на обратной стороне карты")
        String cvc,

        @Size(max = 64, message = "Имя владельца — не длиннее 64 символов")
        String holder) {

    public CardPaymentRequest {
        // Номер вводят группами по 4 цифры, срок — иногда как «12 / 30»: пробелы внутри убираем
        number = number == null ? null : number.replaceAll("\\s+", "");
        expiry = expiry == null ? null : expiry.replaceAll("\\s+", "");
        cvc = Strings.strip(cvc);
        holder = Strings.strip(holder);
    }

    public static CardPaymentRequest empty() {
        return new CardPaymentRequest("", "", "", "");
    }

    /** Последние 4 цифры — единственное, что можно хранить и показывать. */
    public String last4() {
        return number == null || number.length() < 4 ? "" : number.substring(number.length() - 4);
    }

    /** Номер и CVC не должны попасть в лог, даже если запрос случайно залогируют целиком. */
    @Override
    public String toString() {
        return "CardPaymentRequest[number=•• " + last4() + ", expiry=" + expiry + "]";
    }
}
