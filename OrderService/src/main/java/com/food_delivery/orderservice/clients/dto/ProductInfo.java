package com.food_delivery.orderservice.clients.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductInfo(
    UUID id,
    String name,
    BigDecimal price,
    Boolean available,
    UUID restaurantId
) {}
