package dev.pcshop.order;

public enum Channel {
    SITE("Сайт"), TELEGRAM("Telegram");

    private final String label;

    Channel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
