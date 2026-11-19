package com.food_delivery.CatalogService.dtos.request;

import jakarta.validation.constraints.NotNull;

public record UpdateProductAvailabilityRequest(

    @NotNull (message = "A disponibilidade é obrigatória")
    Boolean available

) {}
