package com.food_delivery.CatalogService.dtos.response;

import java.util.List;
import java.util.UUID;

public record CategoryMenuResponse(

    UUID id,
    String name,
    String description,
    List<ProductResponse> products

) {}
