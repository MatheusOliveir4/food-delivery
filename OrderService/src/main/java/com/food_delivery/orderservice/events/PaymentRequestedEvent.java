package com.food_delivery.orderservice.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.food_delivery.orderservice.entities.enums.PaymentMethod;

public record PaymentRequestedEvent(
    UUID orderId,
    Long customerId,
    BigDecimal amount,
    PaymentMethod paymentMethod,
    LocalDateTime requestedAt
) {}
