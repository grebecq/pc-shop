package dev.pcshop;

import dev.pcshop.catalog.CatalogService;
import dev.pcshop.catalog.ProductQuery;
import dev.pcshop.catalog.ProductRepository;
import dev.pcshop.order.Channel;
import dev.pcshop.order.Customer;
import dev.pcshop.order.OrderException;
import dev.pcshop.order.OrderLine;
import dev.pcshop.order.OrderService;
import dev.pcshop.order.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfig.class)
class ShopFlowTest {

    private static final Customer CUSTOMER = new Customer("Тест", "+7 900 000-00-00", "Москва, ул. Тестовая, 1", null);

    @Autowired CatalogService catalog;
    @Autowired OrderService orders;
    @Autowired ProductRepository products;

    @Test
    void filtersByJsonAttributes() {
        var query = new ProductQuery("cpu", Map.of("socket", List.of("AM5")), null, null, false,
                ProductQuery.Sort.PRICE_ASC, 0, 50);
        var page = catalog.search(query);

        assertThat(page.getContent()).isNotEmpty()
                .allSatisfy(p -> assertThat(p.getAttributes()).containsEntry("socket", "AM5"));
        assertThat(page.getContent()).extracting(p -> p.getPrice()).isSorted();
    }

    @Test
    void numericFacetsAreSortedAsNumbers() {
        var capacities = catalog.facets("ram").get("capacity_gb");
        assertThat(capacities).extracting(f -> f.value()).containsExactly("16", "32", "64");
    }

    @Test
    void unknownFilterKeysAreIgnored() {
        var query = new ProductQuery("gpu", Map.of("'; drop table product; --", List.of("x")), null, null, false,
                ProductQuery.Sort.POPULAR, 0, 50);
        assertThat(catalog.search(query).getTotalElements()).isEqualTo(7);
    }

    @Test
    void orderReservesStockAndCancelReturnsIt() {
        var ssd = catalog.product("kingston-nv3-1-tb");
        int before = ssd.getStock();

        var order = orders.create(Channel.SITE, CUSTOMER, List.of(new OrderLine(ssd.getId(), 2), new OrderLine(ssd.getId(), 1)), null);

        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getTotal()).isEqualTo(ssd.getPrice() * 3);
        assertThat(products.findById(ssd.getId()).orElseThrow().getStock()).isEqualTo(before - 3);

        orders.markPaid(order.getPublicId());
        assertThat(orders.byPublicId(order.getPublicId()).getStatus()).isEqualTo(OrderStatus.PAID);

        orders.changeStatus(order.getId(), OrderStatus.CANCELLED);
        assertThat(products.findById(ssd.getId()).orElseThrow().getStock()).isEqualTo(before);
    }

    @Test
    void orderFailsWholeWhenStockIsShort() {
        var psu = catalog.product("seasonic-vertex-gx-1000-1000-vt");
        var case_ = catalog.product("deepcool-cc560");
        int caseStock = case_.getStock();

        assertThatThrownBy(() -> orders.create(Channel.SITE, CUSTOMER,
                List.of(new OrderLine(case_.getId(), 1), new OrderLine(psu.getId(), psu.getStock() + 1)), null))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("Недостаточно на складе");

        // Резерв по первой позиции тоже откатился вместе с транзакцией
        assertThat(products.findById(case_.getId()).orElseThrow().getStock()).isEqualTo(caseStock);
    }
}
