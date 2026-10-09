package dev.pcshop.entity;

import dev.pcshop.util.Formats;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Промокод: скидка в процентах или в рублях на сумму товаров, при заказе от min_total. Заводится в базе. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "promo_code")
public class PromoCode {

    @Id
    private String code;

    /** Скидка в процентах или null, если скидка в рублях. */
    private Integer percent;

    /** Скидка в рублях или null, если скидка в процентах. */
    private Integer amount;

    @Column(name = "min_total", nullable = false)
    private int minTotal;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean active;

    public boolean expired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    /** Скидка на сумму товаров: процент округляем вниз до рубля, сумма в рублях — не больше самих товаров. */
    public int discountFor(int itemsTotal) {
        return percent != null ? (int) ((long) itemsTotal * percent / 100) : Math.min(amount, itemsTotal);
    }

    /** «−10% на заказ от 30 000 ₽» — для корзины и страницы скидок. */
    public String title() {
        String value = percent != null ? "−" + percent + "%" : "−" + Formats.price(amount);
        return minTotal > 0 ? value + " на заказ от " + Formats.price(minTotal) : value + " на заказ";
    }
}
