package com.food_delivery.orderservice.clients;

import java.util.Optional;
import java.util.UUID;

import com.food_delivery.orderservice.clients.dto.ProductInfo;

public interface CatalogClient {
    Optional<ProductInfo> findProduct(UUID productId);
}
