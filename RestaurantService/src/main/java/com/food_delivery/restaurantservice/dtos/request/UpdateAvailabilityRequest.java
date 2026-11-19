package com.food_delivery.restaurantservice.dtos.request;

import jakarta.validation.constraints.NotNull;

public record UpdateAvailabilityRequest(

    @NotNull (message = "Informe se o restaurante está aberto (true) ou fechado (false)")
    Boolean open

) {}
