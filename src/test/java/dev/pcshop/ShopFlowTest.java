package dev.pcshop;

import dev.pcshop.dto.request.CustomerRequest;
import dev.pcshop.dto.request.OrderLineRequest;
import dev.pcshop.dto.request.ProductSearchRequest;
import dev.pcshop.dto.request.ProductUpdateRequest;
import dev.pcshop.enums.Channel;
import dev.pcshop.enums.OrderStatus;
import dev.pcshop.exception.OrderException;
import dev.pcshop.repository.ProductRepository;
import dev.pcshop.service.CatalogService;
import dev.pcshop.service.OrderService;
import jakarta.validation.ConstraintViolationException;
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

    private static final CustomerRequest CUSTOMER = new CustomerRequest("Тест", "+7 900 000-00-00", "Москва, ул. Тестовая, 1", null);

    @Autowired CatalogService catalog;
    @Autowired OrderService orders;
    @Autowired ProductRepository products;

    @Test
    void filtersByJsonAttributes() {
        var query = new ProductSearchRequest("cpu", Map.of("socket", List.of("AM5")), null, null, false,
                ProductSearchRequest.Sort.PRICE_ASC, 0, 50);
        var page = catalog.search(query);

        assertThat(page.getContent()).isNotEmpty()
                .allSatisfy(p -> assertThat(p.getAttributes()).containsEntry("socket", "AM5"));
        assertThat(page.getContent()).extracting(p -> p.getPrice()).isSorted();
    }

    @Test
    void numericFacetsAreSortedAsNumbers() {
        var capacities = catalog.facets("ram").get("capacity_gb");
        assertThat(capacities).extracting(f -> f.value()).containsExactly("16", "32", "64", "96");
    }

    @Test
    void unknownFilterKeysAreIgnored() {
        var query = new ProductSearchRequest("gpu", Map.of("'; drop table product; --", List.of("x")), null, null, false,
                ProductSearchRequest.Sort.POPULAR, 0, 50);
        assertThat(catalog.search(query).getTotalElements()).isEqualTo(30);
    }

    @Test
    void orderReservesStockAndCancelReturnsIt() {
        var ssd = catalog.product("kingston-nv3-1-tb-m-2-nvme");
        int before = ssd.getStock();

        var order = orders.create(Channel.SITE, CUSTOMER, List.of(new OrderLineRequest(ssd.getId(), 2), new OrderLineRequest(ssd.getId(), 1)), null);

        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getTotal()).isEqualTo(ssd.getPrice() * 3);
        assertThat(products.findById(ssd.getId()).orElseThrow().getStock()).isEqualTo(before - 3);

        orders.markPaid(order.getPublicId());
        assertThat(orders.byPublicId(order.getPublicId()).getStatus()).isEqualTo(OrderStatus.PAID);

        orders.changeStatus(order.getId(), OrderStatus.CANCELLED);
        assertThat(products.findById(ssd.getId()).orElseThrow().getStock()).isEqualTo(before);
    }

    @Test
    void orderServiceChecksCustomerForEveryChannel() {
        long ssd = catalog.product("kingston-nv3-1-tb-m-2-nvme").getId();
        // Бот проверяет ввод сам, но сервис не доверяет никакому каналу
        var bad = new CustomerRequest(" ", "не телефон", "Мск", null);
        assertThatThrownBy(() -> orders.create(Channel.TELEGRAM, bad, List.of(new OrderLineRequest(ssd, 1)), 1L))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("Укажите имя")
                .hasMessageContaining("Телефон в формате");
        assertThatThrownBy(() -> orders.create(Channel.TELEGRAM, CUSTOMER, List.of(), 1L))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void adminEditRefreshesUpdatedAt() {
        var gpu = catalog.product("palit-geforce-rtx-5070-infinity-3-12-gb");
        var before = gpu.getUpdatedAt();
        catalog.updateProduct(gpu.getId(), new ProductUpdateRequest(gpu.getPrice() + 100, gpu.getStock(), true));
        assertThat(products.findById(gpu.getId()).orElseThrow().getUpdatedAt()).isAfter(before);
    }

    @Test
    void orderFailsWholeWhenStockIsShort() {
        var psu = catalog.product("corsair-rm1000x-2024-1000-vt");
        var case_ = catalog.product("deepcool-cc560-v2-black");
        int caseStock = case_.getStock();

        assertThatThrownBy(() -> orders.create(Channel.SITE, CUSTOMER,
                List.of(new OrderLineRequest(case_.getId(), 1), new OrderLineRequest(psu.getId(), psu.getStock() + 1)), null))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("Недостаточно на складе");

        // Резерв по первой позиции тоже откатился вместе с транзакцией
        assertThat(products.findById(case_.getId()).orElseThrow().getStock()).isEqualTo(caseStock);
    }
}
