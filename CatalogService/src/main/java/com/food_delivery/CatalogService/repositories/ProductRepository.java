package com.food_delivery.CatalogService.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.food_delivery.CatalogService.entities.Product;

public interface ProductRepository
        extends JpaRepository<Product, UUID> {

    List<Product> findByRestaurantId(UUID restaurantId);

    List<Product> findByRestaurantIdAndAvailableTrue(
        UUID restaurantId
    );

    List<Product> findByCategoryId(UUID categoryId);

    List<Product> findByRestaurantIdAndCategoryId(
        UUID restaurantId,
        UUID categoryId
    );

    boolean existsByCategoryId(UUID categoryId);
}
