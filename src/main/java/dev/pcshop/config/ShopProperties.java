package dev.pcshop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("shop")
public record ShopProperties(String baseUrl, Admin admin, Telegram telegram) {

    public record Admin(String username, String password) {
    }

    public record Telegram(String botToken, String botUsername, Long adminChatId, String paymentToken) {

        public boolean enabled() {
            return botToken != null && !botToken.isBlank();
        }

        /** Ссылка на бота для кнопок на сайте или null, если имя бота не задано. */
        public String botLink() {
            return botUsername == null || botUsername.isBlank() ? null : "https://t.me/" + botUsername.replace("@", "");
        }

        public boolean realPayments() {
            return paymentToken != null && !paymentToken.isBlank();
        }
    }
}
