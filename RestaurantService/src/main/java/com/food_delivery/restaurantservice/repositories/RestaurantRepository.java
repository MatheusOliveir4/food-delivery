package com.food_delivery.restaurantservice.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.food_delivery.restaurantservice.entities.Restaurant;

public interface RestaurantRepository
        extends JpaRepository<Restaurant, UUID> {

    // restaurantes de um dono -> WHERE owner_id = ?
    List<Restaurant> findByOwnerId(UUID ownerId);

    // restaurantes abertos -> WHERE open = true
    List<Restaurant> findByOpenTrue();
}
