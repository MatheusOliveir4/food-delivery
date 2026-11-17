package com.fooddelivery.userservice.dto;

import com.fooddelivery.userservice.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, message = "a senha deve ter pelo menos 8 caracteres") String password,
        String phone,
        @NotNull Role role) {
}
