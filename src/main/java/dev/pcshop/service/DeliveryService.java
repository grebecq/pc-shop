package dev.pcshop.service;

import dev.pcshop.config.ShopProperties;
import dev.pcshop.entity.PickupPoint;
import dev.pcshop.enums.DeliveryMethod;
import dev.pcshop.exception.BadRequestException;
import dev.pcshop.repository.PickupPointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Способы получения: магазины для самовывоза и цена доставки курьером. */
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final PickupPointRepository pickupPoints;
    private final ShopProperties props;

    @Transactional(readOnly = true)
    public List<PickupPoint> pickupPoints() {
        return pickupPoints.findAllByActiveTrueOrderById();
    }

    @Transactional(readOnly = true)
    public PickupPoint pickupPoint(Long id) {
        if (id == null) {
            throw new BadRequestException("Выберите магазин для самовывоза");
        }
        return pickupPoints.findByIdAndActiveTrue(id).orElseThrow(() -> new BadRequestException("Этого магазина нет в списке"));
    }

    /** Самовывоз бесплатный, курьер — бесплатный от суммы free-from. */
    public int price(DeliveryMethod method, int itemsTotal) {
        if (method == DeliveryMethod.PICKUP || itemsTotal >= props.delivery().freeFrom()) {
            return 0;
        }
        return props.delivery().courierPrice();
    }

    public int courierPrice() {
        return props.delivery().courierPrice();
    }

    public int freeFrom() {
        return props.delivery().freeFrom();
    }
}
