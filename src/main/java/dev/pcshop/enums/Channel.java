package dev.pcshop.enums;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum Channel {
    SITE("Сайт"), TELEGRAM("Telegram");

    private final String label;

    public String label() {
        return label;
    }
}
