package dev.pcshop.dto.response;

/**
 * Условия промокода для корзины: скидку браузер считает по той же формуле, что и сервер (PromoCode.discountFor).
 *
 * @param percent  скидка в процентах или null
 * @param amount   скидка в рублях или null
 * @param minTotal от какой суммы товаров действует
 * @param title    «−10% на заказ от 30 000 ₽»
 */
public record PromoResponse(String code, Integer percent, Integer amount, int minTotal, String title) {
}
