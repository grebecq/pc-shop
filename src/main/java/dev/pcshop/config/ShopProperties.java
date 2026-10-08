package dev.pcshop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("shop")
public record ShopProperties(String baseUrl, Admin admin, Telegram telegram) {

    public record Admin(String username, String password) {
    }

    public record Telegram(String botToken, Long adminChatId, String paymentToken) {

        public boolean enabled() {
            return botToken != null && !botToken.isBlank();
        }

        public boolean realPayments() {
            return paymentToken != null && !paymentToken.isBlank();
        }
    }
}
