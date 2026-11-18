package com.food_delivery.restaurantservice.dtos.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record RestaurantResponse(
    UUID id,
    String name,
    String description,
    UUID ownerId,
    Boolean open,
    LocalDateTime createdAt,
    LocalDateTime updatedAt

) {}
