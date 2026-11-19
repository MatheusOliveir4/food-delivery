package com.food_delivery.orderservice.clients.stub;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.food_delivery.orderservice.clients.RestaurantClient;
import com.food_delivery.orderservice.clients.dto.RestaurantInfo;

@Component
@Profile("stub")
public class StubRestaurantClient implements RestaurantClient {
    private static final List<RestaurantInfo> RESTAURANTS = List.of(
        StubData.RESTAURANT_OPEN,
        StubData.RESTAURANT_CLOSED
    );

    @Override
    public Optional<RestaurantInfo> findRestaurant(UUID restaurantId) {
        return RESTAURANTS.stream()
            .filter(restaurant -> restaurant.id().equals(restaurantId))
            .findFirst();
    }
}
