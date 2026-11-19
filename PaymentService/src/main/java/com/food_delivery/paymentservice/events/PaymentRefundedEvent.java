package com.food_delivery.paymentservice.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Publicado depois de um estorno (routing key {@code payment.refunded}). */
public record PaymentRefundedEvent(
    UUID eventId,
    UUID paymentId,
    UUID orderId,
    BigDecimal amount,
    Instant occurredAt
) {
    public static PaymentRefundedEvent of(UUID paymentId, UUID orderId, BigDecimal amount) {
        return new PaymentRefundedEvent(UUID.randomUUID(), paymentId, orderId, amount, Instant.now());
    }
}
