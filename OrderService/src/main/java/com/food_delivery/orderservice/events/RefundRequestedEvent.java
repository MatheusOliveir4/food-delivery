package com.food_delivery.orderservice.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record RefundRequestedEvent(
    UUID orderId,
    Long customerId,
    BigDecimal amount,
    String reason,
    LocalDateTime requestedAt
) {}
