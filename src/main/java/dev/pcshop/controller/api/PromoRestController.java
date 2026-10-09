package dev.pcshop.controller.api;

import dev.pcshop.dto.response.PromoResponse;
import dev.pcshop.entity.PromoCode;
import dev.pcshop.service.PromoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Заказы")
@Validated
@RestController
@RequestMapping("/api/promo")
@RequiredArgsConstructor
public class PromoRestController {

    private final PromoService promoService;

    @Operation(summary = "Проверить промокод",
            description = "Условия действующего промокода. Неизвестный или истёкший код — 400 с текстом для покупателя. "
                    + "Скидку при оформлении заказа сервер считает сам.")
    @GetMapping("/{code}")
    public PromoResponse check(@Parameter(example = "SBORKA10") @PathVariable
                               @Size(max = 32, message = "Такого промокода нет") String code) {
        PromoCode promo = promoService.find(code);
        return new PromoResponse(promo.getCode(), promo.getPercent(), promo.getAmount(), promo.getMinTotal(), promo.title());
    }
}
