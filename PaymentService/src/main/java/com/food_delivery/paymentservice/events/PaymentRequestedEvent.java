package com.food_delivery.paymentservice.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import com.food_delivery.paymentservice.entities.enums.PaymentMethod;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Recebido do Order Service (routing key {@code payment.requested}). */
public record PaymentRequestedEvent(
    @NotNull UUID eventId,
    @NotNull UUID orderId,
    @NotNull Long customerId,
    @NotNull @Positive BigDecimal amount,
    @NotNull PaymentMethod paymentMethod,
    LocalDateTime requestedAt,
    Instant occurredAt
) {}
