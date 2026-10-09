package dev.pcshop.service;

import dev.pcshop.entity.PromoCode;
import dev.pcshop.exception.BadRequestException;
import dev.pcshop.repository.PromoCodeRepository;
import dev.pcshop.util.Formats;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

/** Промокоды: проверка в корзине и пересчёт скидки при оформлении заказа. */
@Service
@RequiredArgsConstructor
public class PromoService {

    private final PromoCodeRepository promoCodes;

    /** Действующий промокод. Регистр и пробелы по краям не важны: «sborka10 » — то же, что «SBORKA10». */
    @Transactional(readOnly = true)
    public PromoCode find(String code) {
        PromoCode promo = promoCodes.findById(normalize(code)).filter(PromoCode::isActive)
                .orElseThrow(() -> new BadRequestException("Такого промокода нет"));
        if (promo.expired(Instant.now())) {
            throw new BadRequestException("Срок действия промокода " + promo.getCode() + " закончился");
        }
        return promo;
    }

    /**
     * Скидка на сумму товаров. Если сумма меньше порога, заказ не оформляем, а не молча убираем скидку:
     * покупатель видел её в корзине и рассчитывает на неё.
     */
    public int discount(PromoCode promo, int itemsTotal) {
        if (itemsTotal < promo.getMinTotal()) {
            throw new BadRequestException("Промокод " + promo.getCode() + " действует на заказ от " + Formats.price(promo.getMinTotal()));
        }
        return promo.discountFor(itemsTotal);
    }

    private static String normalize(String code) {
        return code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
    }
}
