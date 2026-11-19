package com.food_delivery.paymentservice.events;

import java.time.Instant;
import java.util.UUID;

/** Publicado quando a cobrança é aprovada (routing key {@code payment.approved}). */
public record PaymentApprovedEvent(
    UUID eventId,
    UUID paymentId,
    UUID orderId,
    UUID transactionId,
    Instant occurredAt
) {
    public static PaymentApprovedEvent of(UUID paymentId, UUID orderId, UUID transactionId) {
        return new PaymentApprovedEvent(UUID.randomUUID(), paymentId, orderId, transactionId, Instant.now());
    }
}
