package com.food_delivery.paymentservice.dtos.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.food_delivery.paymentservice.entities.enums.TransactionStatus;
import com.food_delivery.paymentservice.entities.enums.TransactionType;

public record PaymentTransactionResponse(
    UUID id,
    TransactionType type,
    TransactionStatus status,
    BigDecimal amount,
    String reason,
    LocalDateTime createdAt
) {}
