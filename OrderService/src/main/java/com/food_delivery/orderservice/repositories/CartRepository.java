package com.food_delivery.orderservice.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.food_delivery.orderservice.entities.Cart;

public interface CartRepository
        extends JpaRepository<Cart, UUID> {
    Optional<Cart> findByCustomerId(Long customerId);
}
