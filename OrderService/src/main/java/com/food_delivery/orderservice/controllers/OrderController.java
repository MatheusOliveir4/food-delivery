package com.food_delivery.orderservice.controllers;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.food_delivery.orderservice.dtos.request.CheckoutRequest;
import com.food_delivery.orderservice.dtos.request.RejectOrderRequest;
import com.food_delivery.orderservice.dtos.request.UpdatePreparationRequest;
import com.food_delivery.orderservice.dtos.response.OrderResponse;
import com.food_delivery.orderservice.entities.enums.OrderStatus;
import com.food_delivery.orderservice.security.AuthenticatedUser;
import com.food_delivery.orderservice.services.OrderService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/orders")
public class OrderController {
    @Autowired
    private OrderService orderService;

    @PostMapping("/checkout")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderResponse> checkout(
        @AuthenticationPrincipal AuthenticatedUser user,
        @Valid @RequestBody CheckoutRequest request
    ) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(orderService.checkout(user.getId(), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> findById(
        @AuthenticationPrincipal AuthenticatedUser user,
        @PathVariable UUID id
    ) {
        OrderResponse order = orderService.findById(id);

        boolean isStaff = "ADMIN".equals(user.getRole()) || "RESTAURANT_OWNER".equals(user.getRole());

        if (!isStaff && !order.customerId().equals(user.getId())) {
            throw new AccessDeniedException("Acesso negado ao pedido");
        }

        return ResponseEntity.ok(order);
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasRole('ADMIN') or #customerId == authentication.principal.id")
    public ResponseEntity<List<OrderResponse>> findByCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(orderService.findByCustomer(customerId));
    }

    @GetMapping("/restaurant/{restaurantId}")
    @PreAuthorize("hasAnyRole('RESTAURANT_OWNER', 'ADMIN')")
    public ResponseEntity<List<OrderResponse>> findByRestaurant(
        @PathVariable UUID restaurantId,
        @RequestParam(required = false) OrderStatus status
    ) {
        return ResponseEntity.ok(orderService.findByRestaurant(restaurantId, status));
    }

    @PatchMapping("/{id}/accept")
    @PreAuthorize("hasAnyRole('RESTAURANT_OWNER', 'ADMIN')")
    public ResponseEntity<OrderResponse> accept(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.accept(id));
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('RESTAURANT_OWNER', 'ADMIN')")
    public ResponseEntity<OrderResponse> reject(
        @PathVariable UUID id,
        @Valid @RequestBody(required = false) RejectOrderRequest request
    ) {
        return ResponseEntity.ok(orderService.reject(id, request));
    }

    @PatchMapping("/{id}/preparation")
    @PreAuthorize("hasAnyRole('RESTAURANT_OWNER', 'ADMIN')")
    public ResponseEntity<OrderResponse> updatePreparation(
        @PathVariable UUID id,
        @Valid @RequestBody UpdatePreparationRequest request
    ) {
        return ResponseEntity.ok(orderService.updatePreparation(id, request));
    }
}
