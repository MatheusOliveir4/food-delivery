package com.food_delivery.CatalogService.dtos.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProductResponse(

    UUID id,
    String name,
    String description,
    BigDecimal price,
    String imageUrl,
    Boolean available,
    UUID restaurantId,
    UUID categoryId,
    String categoryName,
    LocalDateTime createdAt,
    LocalDateTime updatedAt

) {}

