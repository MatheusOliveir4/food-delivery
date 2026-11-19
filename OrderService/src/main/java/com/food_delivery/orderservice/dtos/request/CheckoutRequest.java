package com.food_delivery.orderservice.dtos.request;

import com.food_delivery.orderservice.entities.enums.PaymentMethod;

import jakarta.validation.constraints.NotNull;

public record CheckoutRequest(

    @NotNull (message = "A forma de pagamento é obrigatória")
    PaymentMethod paymentMethod
) {}
