package com.food_delivery.CatalogService.controllers;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.food_delivery.CatalogService.dtos.request.CreateProductRequest;
import com.food_delivery.CatalogService.dtos.request.UpdateProductAvailabilityRequest;
import com.food_delivery.CatalogService.dtos.request.UpdateProductRequest;
import com.food_delivery.CatalogService.dtos.response.ProductResponse;
import com.food_delivery.CatalogService.services.ProductService;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(
            @Valid @RequestBody CreateProductRequest request) {
        ProductResponse response = productService.save(request);
        return ResponseEntity.created(URI.create("/api/v1/products/" + response.id()))
            .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<List<ProductResponse>> findByRestaurant(
            @PathVariable UUID restaurantId) {
        return ResponseEntity.ok(productService.findByRestaurant(restaurantId));
    }

    @GetMapping("/restaurant/{restaurantId}/available")
    public ResponseEntity<List<ProductResponse>> findAvailableByRestaurant(
            @PathVariable UUID restaurantId) {
        return ResponseEntity.ok(productService.findAvailableByRestaurant(restaurantId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest request) {
        return ResponseEntity.ok(productService.update(id, request));
    }

    @PatchMapping("/{id}/availability")
    public ResponseEntity<ProductResponse> updateAvailability(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductAvailabilityRequest request) {
        return ResponseEntity.ok(productService.updateAvailability(id, request));
    }

    // Exclusão lógica: o produto permanece no banco, mas fica indisponível.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        productService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
