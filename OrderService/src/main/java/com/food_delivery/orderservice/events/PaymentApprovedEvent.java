package com.food_delivery.orderservice.events;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Recebido do Payment Service (routing key {@code payment.approved}). */
public record PaymentApprovedEvent(
    @NotNull UUID eventId,
    @NotNull UUID paymentId,
    @NotNull UUID orderId,
    @NotNull UUID transactionId,
    Instant occurredAt
) {}
