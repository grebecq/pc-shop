package dev.pcshop.enums;

import lombok.RequiredArgsConstructor;

/** Как покупатель получает заказ. */
@RequiredArgsConstructor
public enum DeliveryMethod {
    PICKUP("Самовывоз из магазина"), COURIER("Доставка курьером");

    private final String label;

    public String label() {
        return label;
    }
}
