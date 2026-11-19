package com.food_delivery.orderservice.clients.rest;

import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.food_delivery.orderservice.clients.RestaurantClient;
import com.food_delivery.orderservice.clients.dto.RestaurantInfo;
import com.food_delivery.orderservice.exceptions.ExternalServiceException;

@Component
@Profile("!stub")
public class RestRestaurantClient implements RestaurantClient {
    private final RestClient restClient;

    public RestRestaurantClient(
        RestClient.Builder builder,
        @Value("${app.services.restaurant-url}") String baseUrl
    ) {
        this.restClient = builder
            .baseUrl(baseUrl)
            .build();
    }

    @Override
    public Optional<RestaurantInfo> findRestaurant(UUID restaurantId) {
        try {
            return Optional.ofNullable(
                restClient.get()
                    .uri("/restaurants/{id}", restaurantId)
                    .retrieve()
                    .body(RestaurantInfo.class)
            );
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new ExternalServiceException("Restaurant Service indisponível", e);
        }
    }
}
