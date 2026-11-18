package com.food_delivery.restaurantservice.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRestaurantRequest(

    @NotBlank (message = "O nome é obrigatório")
    @Size (max = 100)
    String name,

    @Size (max = 255)
    String description,

    @NotNull (message = "O proprietário é obrigatório")
    Long ownerId

) {}
