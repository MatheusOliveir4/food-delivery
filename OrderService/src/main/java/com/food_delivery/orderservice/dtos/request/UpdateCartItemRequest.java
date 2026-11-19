package com.food_delivery.orderservice.dtos.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateCartItemRequest(

    @NotNull (message = "A quantidade é obrigatória")
    @Min (value = 1, message = "A quantidade mínima é 1")
    @Max (value = 99, message = "A quantidade máxima é 99")
    Integer quantity
) {}
