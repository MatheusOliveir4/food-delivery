package com.food_delivery.CatalogService.dtos.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record CategoryResponse(
    UUID id,
    String name,
    String description,
    UUID restaurantId,
    Boolean active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt

) {}

