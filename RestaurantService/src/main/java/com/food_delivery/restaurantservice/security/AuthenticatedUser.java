package com.food_delivery.restaurantservice.security;

public record AuthenticatedUser(Long id, String role) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public boolean isRestaurantOwner() {
        return "RESTAURANT_OWNER".equals(role);
    }
}
