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

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "promo_code")
public class PromoCode {

    @Id
    private String code;

    private Integer percent;

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

    public int discountFor(int itemsTotal) {
        return percent != null ? (int) ((long) itemsTotal * percent / 100) : Math.min(amount, itemsTotal);
    }

    public String title() {
        String value = percent != null ? "−" + percent + "%" : "−" + Formats.price(amount);
        return minTotal > 0 ? value + " на заказ от " + Formats.price(minTotal) : value + " на заказ";
    }
}
