package dev.pcshop.dto.request;

import dev.pcshop.enums.DeliveryMethod;
import dev.pcshop.util.Strings;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Оформление заказа на сайте.
 *
 * @param delivery      самовывоз или курьер; если не указан — курьер, как было до самовывоза
 * @param pickupPointId магазин для самовывоза (тогда customer.address игнорируется)
 * @param addressId     сохранённый адрес вошедшего покупателя (тогда customer.address игнорируется)
 * @param newAddress    новый адрес по полям — сохраняется в кабинет, если saveAddress
 * @param consent       согласие на обработку персональных данных. Нужно от гостя: вошедший дал его при регистрации
 * @param promoCode     промокод из корзины; скидку сервер пересчитает сам
 */
public record CreateOrderRequest(

        @NotNull(message = "Укажите данные покупателя")
        @Valid
        CustomerRequest customer,

        @NotEmpty(message = "Корзина пуста")
        @Size(max = 50, message = "Слишком много позиций в заказе")
        List<@NotNull(message = "Пустая позиция в заказе") @Valid OrderLineRequest> items,

        DeliveryMethod delivery,

        @Positive(message = "Неизвестный магазин")
        Long pickupPointId,

        @Positive(message = "Неизвестный адрес")
        Long addressId,

        @Valid
        AddressRequest newAddress,

        Boolean saveAddress,

        Boolean consent,

        @Size(max = 32, message = "Такого промокода нет")
        String promoCode) {

    public CreateOrderRequest {
        if (delivery == null) {
            delivery = DeliveryMethod.COURIER;
        }
        promoCode = Strings.blankToNull(promoCode);
    }

    public boolean hasConsent() {
        return Boolean.TRUE.equals(consent);
    }

    public boolean wantsToSaveAddress() {
        return delivery == DeliveryMethod.COURIER && addressId == null && newAddress != null && Boolean.TRUE.equals(saveAddress);
    }
}
