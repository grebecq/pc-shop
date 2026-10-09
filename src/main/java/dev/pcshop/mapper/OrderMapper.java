package dev.pcshop.mapper;

import dev.pcshop.dto.response.OrderCreatedResponse;
import dev.pcshop.dto.response.OrderItemResponse;
import dev.pcshop.dto.response.OrderResponse;
import dev.pcshop.entity.Order;
import dev.pcshop.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

// Новое поле в DTO без маппинга — ошибка компиляции, а не тихий null в ответе
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OrderMapper {

    @Mapping(target = "number", expression = "java(order.number())")
    @Mapping(target = "statusLabel", expression = "java(order.statusLabel())")
    OrderResponse toResponse(Order order);

    OrderItemResponse toResponse(OrderItem item);

    @Mapping(target = "number", expression = "java(order.number())")
    @Mapping(target = "paymentUrl", expression = "java(\"/pay/\" + order.getPublicId())")
    OrderCreatedResponse toCreatedResponse(Order order);
}
