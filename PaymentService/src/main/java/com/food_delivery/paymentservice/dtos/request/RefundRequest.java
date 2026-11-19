package com.food_delivery.paymentservice.dtos.request;

import jakarta.validation.constraints.Size;

public record RefundRequest(
    @Size(max = 255, message = "O motivo deve ter no máximo 255 caracteres")
    String reason
) {}
