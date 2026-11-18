package com.food_delivery.orderservice.services;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.food_delivery.orderservice.clients.RestaurantClient;
import com.food_delivery.orderservice.clients.dto.RestaurantInfo;
import com.food_delivery.orderservice.exceptions.ResourceNotFoundException;
import com.food_delivery.orderservice.security.AuthenticatedUser;

@Service
public class RestaurantAccessService {
    @Autowired
    private RestaurantClient restaurantClient;

    public void checkCanManage(AuthenticatedUser user, UUID restaurantId) {
        if ("ADMIN".equals(user.getRole())) {
            return;
        }

        RestaurantInfo restaurant = restaurantClient.findRestaurant(restaurantId)
            .orElseThrow(() ->
                new ResourceNotFoundException(
                    "Restaurante não encontrado: " + restaurantId
                )
            );

        if (!"RESTAURANT_OWNER".equals(user.getRole()) || !user.getId().equals(restaurant.ownerId())) {
            throw new AccessDeniedException("O usuário não é dono do restaurante: " + restaurantId);
        }
    }
}
