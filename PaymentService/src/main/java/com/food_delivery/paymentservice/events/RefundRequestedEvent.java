package com.food_delivery.paymentservice.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Recebido do Order Service (routing key {@code payment.refund-requested}). */
public record RefundRequestedEvent(
    @NotNull UUID eventId,
    @NotNull UUID orderId,
    Long customerId,
    BigDecimal amount,
    String reason,
    LocalDateTime requestedAt,
    Instant occurredAt
) {}
