package com.food_delivery.orderservice.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import com.food_delivery.orderservice.dtos.response.OrderItemResponse;
import com.food_delivery.orderservice.dtos.response.OrderResponse;
import com.food_delivery.orderservice.entities.Order;
import com.food_delivery.orderservice.entities.OrderItem;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface OrderMapper {
    OrderResponse mapToOrderResponse(Order order);

    OrderItemResponse mapToOrderItemResponse(OrderItem item);
}
