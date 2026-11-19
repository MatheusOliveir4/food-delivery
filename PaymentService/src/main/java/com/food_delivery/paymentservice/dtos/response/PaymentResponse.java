package com.food_delivery.paymentservice.dtos.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.food_delivery.paymentservice.entities.enums.PaymentMethod;
import com.food_delivery.paymentservice.entities.enums.PaymentStatus;

public record PaymentResponse(
    UUID id,
    UUID orderId,
    Long customerId,
    BigDecimal amount,
    PaymentMethod method,
    PaymentStatus status,
    List<PaymentTransactionResponse> transactions,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
