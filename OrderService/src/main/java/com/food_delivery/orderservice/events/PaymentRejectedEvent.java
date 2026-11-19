package com.food_delivery.orderservice.events;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Recebido do Payment Service (routing key {@code payment.rejected}). */
public record PaymentRejectedEvent(
    @NotNull UUID eventId,
    @NotNull UUID paymentId,
    @NotNull UUID orderId,
    String reason,
    Instant occurredAt
) {}
