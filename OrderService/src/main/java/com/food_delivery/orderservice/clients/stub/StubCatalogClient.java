package com.food_delivery.orderservice.clients.stub;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.food_delivery.orderservice.clients.CatalogClient;
import com.food_delivery.orderservice.clients.dto.ProductInfo;

@Component
@Profile("stub")
public class StubCatalogClient implements CatalogClient {
    private static final List<ProductInfo> PRODUCTS = List.of(
        StubData.BURGER,
        StubData.FRIES,
        StubData.SOLD_OUT,
        StubData.OTHER_RESTAURANT_PRODUCT
    );

    @Override
    public Optional<ProductInfo> findProduct(UUID productId) {
        return PRODUCTS.stream()
            .filter(product -> product.id().equals(productId))
            .findFirst();
    }
}
