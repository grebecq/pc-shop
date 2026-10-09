package dev.pcshop.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Настройки магазина из application.yml и .env. Проверяются при старте: без пароля админки приложение не запустится. */
@Validated
@ConfigurationProperties("shop")
public record ShopProperties(

        @NotBlank(message = "Задайте SHOP_BASE_URL — адрес сайта для ссылок из бота")
        String baseUrl,

        @NotNull
        @Valid
        Admin admin,

        @NotNull
        @Valid
        Telegram telegram) {

    public record Admin(

            @NotBlank(message = "Задайте ADMIN_USER — логин админки")
            String username,

            // Пароля по умолчанию нет специально: иначе на хостинге легко забыть его сменить
            @NotBlank(message = "Задайте ADMIN_PASSWORD — пароль админки")
            @Size(min = 8, message = "ADMIN_PASSWORD — не короче 8 символов")
            String password) {
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
