package com.food_delivery.orderservice.clients;

import java.util.Optional;
import java.util.UUID;

import com.food_delivery.orderservice.clients.dto.RestaurantInfo;

public interface RestaurantClient {
    Optional<RestaurantInfo> findRestaurant(UUID restaurantId);
}
