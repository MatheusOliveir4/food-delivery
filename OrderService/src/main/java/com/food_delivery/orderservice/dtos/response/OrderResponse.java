package com.food_delivery.orderservice.dtos.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.food_delivery.orderservice.entities.enums.OrderStatus;
import com.food_delivery.orderservice.entities.enums.PaymentMethod;

public record OrderResponse(
    UUID id,
    Long customerId,
    UUID restaurantId,
    OrderStatus status,
    PaymentMethod paymentMethod,
    BigDecimal total,
    String rejectionReason,
    List<OrderItemResponse> items,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
