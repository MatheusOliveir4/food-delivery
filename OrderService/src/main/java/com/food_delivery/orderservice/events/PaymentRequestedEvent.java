package com.food_delivery.orderservice.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import com.food_delivery.orderservice.entities.enums.PaymentMethod;

/**
 * Publicado pelo Order no checkout (routing key {@code payment.requested}).
 * O JSON é o contrato com o Payment Service; não há classe compartilhada entre os serviços.
 */
public record PaymentRequestedEvent(
    UUID eventId,
    UUID orderId,
    Long customerId,
    BigDecimal amount,
    PaymentMethod paymentMethod,
    LocalDateTime requestedAt,
    Instant occurredAt
) {
    public static PaymentRequestedEvent of(UUID orderId, Long customerId, BigDecimal amount,
                                           PaymentMethod paymentMethod) {
        return new PaymentRequestedEvent(
            UUID.randomUUID(), orderId, customerId, amount, paymentMethod,
            LocalDateTime.now(), Instant.now()
        );
    }
}
