package com.food_delivery.orderservice.dtos.request;

import com.food_delivery.orderservice.entities.enums.PreparationStatus;

import jakarta.validation.constraints.NotNull;

public record UpdatePreparationRequest(

    @NotNull (message = "O status de preparação é obrigatório")
    PreparationStatus status
) {}
