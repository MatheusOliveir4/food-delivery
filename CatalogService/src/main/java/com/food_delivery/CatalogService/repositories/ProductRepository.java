package com.food_delivery.CatalogService.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.food_delivery.CatalogService.entities.Product;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findByRestaurantIdOrderByNameAsc(UUID restaurantId);

    List<Product> findByRestaurantIdAndAvailableTrueOrderByNameAsc(UUID restaurantId);

    List<Product> findByRestaurantIdAndAvailableTrueAndCategory_ActiveTrueOrderByNameAsc(
        UUID restaurantId
    );

    boolean existsByCategory_Id(UUID categoryId);
}
