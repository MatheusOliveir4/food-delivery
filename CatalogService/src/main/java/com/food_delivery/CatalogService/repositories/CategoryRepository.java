package com.food_delivery.CatalogService.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.food_delivery.CatalogService.entities.Category;

public interface CategoryRepository
        extends JpaRepository<Category, UUID> {

    List<Category> findByRestaurantId(UUID restaurantId);

    List<Category> findByRestaurantIdAndActiveTrue(
        UUID restaurantId
    );

    Optional<Category> findByIdAndRestaurantId(
        UUID id,
        UUID restaurantId
    );

    boolean existsByRestaurantIdAndNameIgnoreCase(
        UUID restaurantId,
        String name
    );
}
