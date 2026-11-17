package com.fooddelivery.userservice.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateUserRequest(
        @NotBlank String name,
        String phone) {
}
