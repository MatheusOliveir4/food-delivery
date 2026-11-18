package com.food_delivery.orderservice.controllers;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.food_delivery.orderservice.services.OrderService;

@RestController
@Profile("stub")
@RequestMapping("/dev/payments")
public class DevPaymentController {
    @Autowired
    private OrderService orderService;

    @PostMapping("/{orderId}/approve")
    public ResponseEntity<Void> approve(@PathVariable UUID orderId) {
        orderService.handlePaymentApproved(orderId);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{orderId}/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID orderId) {
        orderService.handlePaymentRejected(orderId, "Pagamento recusado (simulado)");

        return ResponseEntity.noContent().build();
    }
}
