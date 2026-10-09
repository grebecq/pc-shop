package dev.pcshop.service;

import dev.pcshop.TestcontainersConfig;
import dev.pcshop.dto.request.CustomerRequest;
import dev.pcshop.dto.request.OrderLineRequest;
import dev.pcshop.entity.Order;
import dev.pcshop.enums.Channel;
import dev.pcshop.enums.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Неоплаченные заказы не держат склад вечно. */
@SpringBootTest
@Import(TestcontainersConfig.class)
class OrderExpiryTest {

    private static final CustomerRequest CUSTOMER = new CustomerRequest("Тест", "+7 900 000-00-00", "Москва", null);

    @Autowired OrderService orders;
    @Autowired OrderExpiryScheduler expiry;
    @Autowired CatalogService catalog;
    @Autowired JdbcClient jdbc;

    @Test
    void unpaidOrderIsCancelledAndStockReturned() {
        var psu = catalog.product("seasonic-focus-gx-1000-atx-3-1-1000-vt");
        int before = psu.getStock();
        Order stale = orders.create(Channel.SITE, CUSTOMER, List.of(new OrderLineRequest(psu.getId(), 1)), null);
        Order fresh = orders.create(Channel.SITE, CUSTOMER, List.of(new OrderLineRequest(psu.getId(), 1)), null);
        Order paid = orders.create(Channel.SITE, CUSTOMER, List.of(new OrderLineRequest(psu.getId(), 1)), null);
        orders.markPaid(paid.getPublicId());
        ageOrder(stale);
        ageOrder(paid);

        expiry.cancelExpired();

        assertThat(orders.byId(stale.getId()).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(orders.byId(fresh.getId()).getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        assertThat(orders.byId(paid.getId()).getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(catalog.product("seasonic-focus-gx-1000-atx-3-1-1000-vt").getStock()).isEqualTo(before - 2);

        // Опоздавшая оплата отменённый заказ не «оживляет»
        assertThat(orders.markPaid(stale.getPublicId()).getPaidAt()).isNull();
    }

    @Test
    void paidOrderCannotGoBackToAwaitingPayment() {
        var ssd = catalog.product("kingston-nv3-1-tb-m-2-nvme");
        Order order = orders.create(Channel.SITE, CUSTOMER, List.of(new OrderLineRequest(ssd.getId(), 1)), null);
        orders.markPaid(order.getPublicId());
        assertThatThrownBy(() -> orders.changeStatus(order.getId(), OrderStatus.AWAITING_PAYMENT))
                .isInstanceOf(IllegalStateException.class);
    }

    private void ageOrder(Order order) {
        jdbc.sql("UPDATE customer_order SET created_at = now() - interval '2 hours' WHERE id = :id")
                .param("id", order.getId()).update();
    }
}
