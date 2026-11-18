package com.food_delivery.orderservice.clients.dto;

import java.util.UUID;

public record RestaurantInfo(
    UUID id,
    String name,
    Boolean open
) {}
