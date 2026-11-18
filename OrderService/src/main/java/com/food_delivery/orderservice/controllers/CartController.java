package com.food_delivery.orderservice.controllers;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.food_delivery.orderservice.dtos.request.AddCartItemRequest;
import com.food_delivery.orderservice.dtos.request.UpdateCartItemRequest;
import com.food_delivery.orderservice.dtos.response.CartResponse;
import com.food_delivery.orderservice.security.AuthenticatedUser;
import com.food_delivery.orderservice.services.CartService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/cart")
public class CartController {
    @Autowired
    private CartService cartService;

    @PostMapping("/items")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CartResponse> addItem(
        @AuthenticationPrincipal AuthenticatedUser user,
        @Valid @RequestBody AddCartItemRequest request
    ) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(cartService.addItem(user.getId(), request));
    }

    @GetMapping("/{customerId}")
    @PreAuthorize("hasRole('ADMIN') or #customerId == authentication.principal.id")
    public ResponseEntity<CartResponse> findByCustomerId(@PathVariable Long customerId) {
        return ResponseEntity.ok(cartService.findByCustomerId(customerId));
    }

    @PutMapping("/items/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CartResponse> updateItem(
        @AuthenticationPrincipal AuthenticatedUser user,
        @PathVariable UUID id,
        @Valid @RequestBody UpdateCartItemRequest request
    ) {
        return ResponseEntity.ok(cartService.updateItem(user.getId(), id, request));
    }

    @DeleteMapping("/items/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Void> removeItem(
        @AuthenticationPrincipal AuthenticatedUser user,
        @PathVariable UUID id
    ) {
        cartService.removeItem(user.getId(), id);

        return ResponseEntity.noContent().build();
    }
}
