package com.fooddelivery.userservice.dto;

public record LoginResponse(String token, String tokenType, UserResponse user) {
}
