package com.food_delivery.restaurantservice.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.food_delivery.restaurantservice.entities.Restaurant;

public interface RestaurantRepository
        extends JpaRepository<Restaurant, UUID> {

    List<Restaurant> findByOwnerId(Long ownerId);

    List<Restaurant> findByOpenTrue();
}
