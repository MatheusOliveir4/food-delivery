package com.food_delivery.restaurantservice.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateRestaurantRequest(

    @NotBlank (message = "O nome é obrigatório")
    @Size (max = 100)
    String name,

    @Size (max = 255)
    String description

) {}
