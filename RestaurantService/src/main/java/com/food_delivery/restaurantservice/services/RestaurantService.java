package com.food_delivery.restaurantservice.services;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.food_delivery.restaurantservice.dtos.request.CreateRestaurantRequest;
import com.food_delivery.restaurantservice.dtos.request.UpdateAvailabilityRequest;
import com.food_delivery.restaurantservice.dtos.request.UpdateRestaurantRequest;
import com.food_delivery.restaurantservice.dtos.response.RestaurantResponse;
import com.food_delivery.restaurantservice.entities.Restaurant;
import com.food_delivery.restaurantservice.exceptions.ResourceNotFoundException;
import com.food_delivery.restaurantservice.mappers.RestaurantMapper;
import com.food_delivery.restaurantservice.repositories.RestaurantRepository;

@Service
public class RestaurantService {

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private RestaurantMapper restaurantMapper;

    @Transactional(readOnly = true)
    public List<RestaurantResponse> findAll(Boolean open, Long ownerId) {

        List<Restaurant> restaurants;

        if (ownerId != null) {
            restaurants = restaurantRepository.findByOwnerId(ownerId);
        } else if (Boolean.TRUE.equals(open)) {
            restaurants = restaurantRepository.findByOpenTrue();
        } else {
            restaurants = restaurantRepository.findAll();
        }

        return restaurants.stream()
            .map(restaurantMapper::mapToRestaurantResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public RestaurantResponse findById(UUID id) {

        Restaurant entity = findEntityById(id);

        return restaurantMapper.mapToRestaurantResponse(entity);
    }

    @Transactional
    public RestaurantResponse save(CreateRestaurantRequest request) {

        Restaurant entity = restaurantMapper.mapToRestaurant(request);

        entity = restaurantRepository.save(entity);

        return restaurantMapper.mapToRestaurantResponse(entity);
    }

    @Transactional
    public RestaurantResponse update(UUID id, UpdateRestaurantRequest request) {

        Restaurant entity = findEntityById(id);

        restaurantMapper.updateRestaurant(request, entity);

        entity = restaurantRepository.saveAndFlush(entity);

        return restaurantMapper.mapToRestaurantResponse(entity);
    }

    @Transactional
    public RestaurantResponse updateAvailability(UUID id, UpdateAvailabilityRequest request) {

        Restaurant entity = findEntityById(id);

        entity.setOpen(request.open());

        entity = restaurantRepository.saveAndFlush(entity);

        return restaurantMapper.mapToRestaurantResponse(entity);
    }

    private Restaurant findEntityById(UUID id) {
        return restaurantRepository.findById(id)
            .orElseThrow(() ->
                new ResourceNotFoundException(
                    "Restaurante não encontrado: " + id
                )
            );
    }
}
