package com.food_delivery.CatalogService.dtos.request;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateProductRequest(

    @NotBlank (message = "O nome é obrigatório")
    @Size (max = 120)
    String name,

    @Size(max = 500)
    String description,

    @NotNull (message = "O preço é obrigatório")
    @DecimalMin (
        value = "0.01",
        message = "O preço deve ser maior que zero"
    )
    @Digits (integer = 8, fraction = 2)
    BigDecimal price,

    String imageUrl,

    @NotNull(message = "A categoria é obrigatória")
    UUID categoryId,

    @NotNull(message = "O restaurante é obrigatório")
    UUID restaurantId

) {}

