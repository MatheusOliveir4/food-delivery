package com.food_delivery.CatalogService.dtos.request;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateProductRequest(

    @NotBlank (message = "O nome é obrigatório")
    @Size (max = 120)
    String name,

    @Size(max = 500)
    String description,

    @NotNull 
    @DecimalMin ("0.01")
    @Digits (integer = 8, fraction = 2)
    BigDecimal price,


    String imageUrl,

    @NotNull
    UUID categoryId

) {}

