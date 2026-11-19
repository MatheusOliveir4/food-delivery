package com.food_delivery.orderservice.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Publicado pelo Order quando o restaurante rejeita um pedido pago
 * (routing key {@code payment.refund-requested}).
 */
public record RefundRequestedEvent(
    UUID eventId,
    UUID orderId,
    Long customerId,
    BigDecimal amount,
    String reason,
    LocalDateTime requestedAt,
    Instant occurredAt
) {
    public static RefundRequestedEvent of(UUID orderId, Long customerId, BigDecimal amount, String reason) {
        return new RefundRequestedEvent(
            UUID.randomUUID(), orderId, customerId, amount, reason,
            LocalDateTime.now(), Instant.now()
        );
    }
}
