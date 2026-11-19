package com.food_delivery.orderservice.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Recebido do Payment Service (routing key {@code payment.refunded}). */
public record PaymentRefundedEvent(
    @NotNull UUID eventId,
    @NotNull UUID paymentId,
    @NotNull UUID orderId,
    BigDecimal amount,
    Instant occurredAt
) {}
