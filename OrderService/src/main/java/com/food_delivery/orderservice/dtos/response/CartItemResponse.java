package com.food_delivery.orderservice.dtos.response;

import java.math.BigDecimal;
import java.util.UUID;

public record CartItemResponse(
    UUID id,
    UUID productId,
    String productName,
    BigDecimal unitPrice,
    Integer quantity,
    BigDecimal total
) {}
