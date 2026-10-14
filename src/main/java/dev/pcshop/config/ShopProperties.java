package dev.pcshop.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
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
        Telegram telegram,

        @NotNull
        @Valid
        Delivery delivery,

        @NotNull
        @Valid
        Yookassa yookassa) {

    /**
     * Тестовая оплата без денег (форма карты на сайте, кнопка в боте) работает, только пока не подключена настоящая:
     * иначе ею можно было бы «оплатить» любой заказ бесплатно.
     */
    public boolean demoPayments() {
        return !yookassa.enabled() && !telegram.realPayments();
    }

    /** Адрес сайта без «/» на конце: к нему дописываются пути ссылок. */
    public String siteUrl() {
        return baseUrl.replaceAll("/+$", "");
    }

    public record Admin(

            @NotBlank(message = "Задайте ADMIN_USER — логин админки")
            String username,

            // Пароля по умолчанию нет специально: иначе на хостинге легко забыть его сменить
            @NotBlank(message = "Задайте ADMIN_PASSWORD — пароль админки")
            @Size(min = 8, message = "ADMIN_PASSWORD — не короче 8 символов")
            String password) {
    }

    /** Цена доставки курьером в рублях и сумма заказа, от которой она бесплатная. Самовывоз всегда бесплатный. */
    public record Delivery(

            @NotNull(message = "Задайте shop.delivery.courier-price")
            @PositiveOrZero(message = "shop.delivery.courier-price — не меньше 0")
            Integer courierPrice,

            @NotNull(message = "Задайте shop.delivery.free-from")
            @PositiveOrZero(message = "shop.delivery.free-from — не меньше 0")
            Integer freeFrom) {
    }

    /**
     * ЮKassa: shopId и секретный ключ из личного кабинета (Интеграция → Ключи API).
     * Оба пустые — на сайте тестовая форма оплаты без денег.
     */
    public record Yookassa(String shopId, String secretKey,
                           @NotBlank(message = "Задайте shop.yookassa.api-url") String apiUrl) {

        public boolean enabled() {
            return !blank(shopId) && !blank(secretKey);
        }

        // Один ключ без другого — почти наверняка опечатка в .env: лучше не запуститься, чем молча остаться на тестовой оплате
        @AssertTrue(message = "Задайте оба ключа ЮKassa: YOOKASSA_SHOP_ID и YOOKASSA_SECRET_KEY")
        public boolean isComplete() {
            return blank(shopId) == blank(secretKey);
        }

        private static boolean blank(String s) {
            return s == null || s.isBlank();
        }
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
