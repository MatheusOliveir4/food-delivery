package com.food_delivery.orderservice.clients.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RestaurantInfo(
    UUID id,
    String name,
    Long ownerId,
    Boolean open
) {}
