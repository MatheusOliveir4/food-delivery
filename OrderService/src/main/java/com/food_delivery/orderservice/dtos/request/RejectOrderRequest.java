package com.food_delivery.orderservice.dtos.request;

import jakarta.validation.constraints.Size;

public record RejectOrderRequest(

    @Size (max = 255)
    String reason
) {}
