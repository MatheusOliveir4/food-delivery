package com.food_delivery.orderservice.controllers;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.food_delivery.orderservice.services.OrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@ConditionalOnProperty(name = "app.payments.simulator-enabled", havingValue = "true")
@RequestMapping("/dev/payments")
@Tag(name = "Pagamento simulado", description = "Aprova ou recusa pagamentos à mão (profile stub, sem RabbitMQ)")
public class DevPaymentController {
    @Autowired
    private OrderService orderService;

    @Operation(summary = "Simular PaymentApproved")
    @PostMapping("/{orderId}/approve")
    public ResponseEntity<Void> approve(@PathVariable UUID orderId) {
        orderService.handlePaymentApproved(orderId);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Simular PaymentRejected")
    @PostMapping("/{orderId}/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID orderId) {
        orderService.handlePaymentRejected(orderId, "Pagamento recusado (simulado)");

        return ResponseEntity.noContent().build();
    }
}
