package dev.pcshop.dto.request;

import dev.pcshop.entity.StoreInfo;
import dev.pcshop.util.Strings;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StoreInfoRequest(

        @NotBlank(message = "Укажите телефон")
        @Pattern(regexp = "^$|^[+\\d][\\d\\s()-]{5,30}$", message = "Проверьте номер телефона")
        String phone,

        @NotBlank(message = "Укажите почту")
        @Email(message = "Проверьте почту")
        @Size(max = 128, message = "Почта — не длиннее 128 символов")
        String email,

        @NotBlank(message = "Укажите, когда вы отвечаете")
        @Size(max = 128, message = "Не длиннее 128 символов")
        String hours,

        @Size(max = 256, message = "Не длиннее 256 символов")
        String legalName,

        @Pattern(regexp = "^$|^\\d{10}$|^\\d{12}$", message = "ИНН — 10 цифр у организации или 12 у ИП и самозанятого")
        String inn,

        @Pattern(regexp = "^$|^\\d{13}$|^\\d{15}$", message = "ОГРН — 13 цифр, ОГРНИП — 15")
        String ogrn,

        @Size(max = 512, message = "Не длиннее 512 символов")
        String legalAddress) {

    public StoreInfoRequest {
        phone = Strings.strip(phone);
        email = Strings.strip(email);
        hours = Strings.strip(hours);
        legalName = Strings.strip(legalName);
        inn = Strings.strip(inn);
        ogrn = Strings.strip(ogrn);
        legalAddress = Strings.strip(legalAddress);
    }

    public static StoreInfoRequest of(StoreInfo store) {
        return new StoreInfoRequest(store.getPhone(), store.getEmail(), store.getHours(), store.getLegalName(),
                store.getInn(), store.getOgrn(), store.getLegalAddress());
    }

    @AssertTrue(message = "Укажите и продавца, и его ИНН")
    public boolean isSellerComplete() {
        return isBlank(legalName) == isBlank(inn);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
