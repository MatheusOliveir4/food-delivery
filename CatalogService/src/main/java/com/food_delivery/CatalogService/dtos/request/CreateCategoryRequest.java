package com.food_delivery.CatalogService.dtos.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(

    @NotBlank (message = "O nome é obrigatório")
    @Size (max = 100)
    String name,

    @Size (max = 255)
    String description,

    @NotNull (message = "O restaurante é obrigatório")
    UUID restaurantId

) {}