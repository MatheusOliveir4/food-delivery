package com.food_delivery.CatalogService.dtos.response;

import java.util.List;
import java.util.UUID;

public record MenuResponse(

    UUID restaurantId,
    List<CategoryMenuResponse> categories

) {}

