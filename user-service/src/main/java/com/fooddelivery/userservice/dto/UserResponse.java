package com.fooddelivery.userservice.dto;

import com.fooddelivery.userservice.model.Role;
import com.fooddelivery.userservice.model.User;

public record UserResponse(Long id, String name, String email, String phone, Role role) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getRole());
    }
}
