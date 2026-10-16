package dev.pcshop.enums;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum DeliveryMethod {
    PICKUP("Самовывоз из магазина"), COURIER("Доставка курьером");

    private final String label;

    public String label() {
        return label;
    }
}
