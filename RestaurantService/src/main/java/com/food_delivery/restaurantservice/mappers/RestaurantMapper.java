package com.food_delivery.restaurantservice.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

import com.food_delivery.restaurantservice.dtos.request.CreateRestaurantRequest;
import com.food_delivery.restaurantservice.dtos.request.UpdateRestaurantRequest;
import com.food_delivery.restaurantservice.dtos.response.RestaurantResponse;
import com.food_delivery.restaurantservice.entities.Restaurant;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface RestaurantMapper {

    RestaurantResponse mapToRestaurantResponse(Restaurant restaurant);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "open", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Restaurant mapToRestaurant(CreateRestaurantRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ownerId", ignore = true)
    @Mapping(target = "open", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateRestaurant(
        UpdateRestaurantRequest request,
        @MappingTarget Restaurant restaurant
    );
}
