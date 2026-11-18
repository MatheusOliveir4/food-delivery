package com.food_delivery.orderservice.dtos.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CartResponse(
    UUID id,
    Long customerId,
    UUID restaurantId,
    List<CartItemResponse> items,
    BigDecimal subtotal,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
