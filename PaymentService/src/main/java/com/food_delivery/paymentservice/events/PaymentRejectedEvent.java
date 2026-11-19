package com.food_delivery.paymentservice.events;

import java.time.Instant;
import java.util.UUID;

/** Publicado quando a cobrança é recusada (routing key {@code payment.rejected}). */
public record PaymentRejectedEvent(
    UUID eventId,
    UUID paymentId,
    UUID orderId,
    String reason,
    Instant occurredAt
) {
    public static PaymentRejectedEvent of(UUID paymentId, UUID orderId, String reason) {
        return new PaymentRejectedEvent(UUID.randomUUID(), paymentId, orderId, reason, Instant.now());
    }
}
