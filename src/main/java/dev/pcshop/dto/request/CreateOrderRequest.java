package dev.pcshop.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Оформление заказа на сайте.
 *
 * @param addressId  сохранённый адрес вошедшего покупателя (тогда customer.address игнорируется)
 * @param newAddress новый адрес по полям — сохраняется в кабинет, если saveAddress
 */
public record CreateOrderRequest(
        @NotNull(message = "Укажите данные покупателя") @Valid CustomerRequest customer,
        @NotEmpty(message = "Корзина пуста")
        @Size(max = 50, message = "Слишком много позиций в заказе") List<@NotNull @Valid OrderLineRequest> items,
        @Positive Long addressId,
        @Valid AddressRequest newAddress,
        Boolean saveAddress) {

    public boolean wantsToSaveAddress() {
        return addressId == null && newAddress != null && Boolean.TRUE.equals(saveAddress);
    }
}
