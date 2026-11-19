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
import com.food_delivery.restaurantservice.exceptions.ForbiddenException;
import com.food_delivery.restaurantservice.exceptions.ResourceNotFoundException;
import com.food_delivery.restaurantservice.mappers.RestaurantMapper;
import com.food_delivery.restaurantservice.repositories.RestaurantRepository;
import com.food_delivery.restaurantservice.security.AuthenticatedUser;

import lombok.extern.slf4j.Slf4j;

@Slf4j
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

        log.info("Listando restaurantes open={} ownerId={} total={}", open, ownerId, restaurants.size());

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
    public RestaurantResponse save(CreateRestaurantRequest request, AuthenticatedUser user) {

        if (!user.isAdmin()) {
            if (!user.isRestaurantOwner()) {
                throw new ForbiddenException("Apenas donos de restaurante ou ADMIN podem cadastrar restaurantes");
            }
            if (!user.id().equals(request.ownerId())) {
                throw new ForbiddenException("O proprietário do restaurante deve ser o usuário autenticado");
            }
        }

        Restaurant entity = restaurantMapper.mapToRestaurant(request);

        entity = restaurantRepository.save(entity);

        log.info("Restaurante criado id={} ownerId={}", entity.getId(), entity.getOwnerId());

        return restaurantMapper.mapToRestaurantResponse(entity);
    }

    @Transactional
    public RestaurantResponse update(UUID id, UpdateRestaurantRequest request, AuthenticatedUser user) {

        Restaurant entity = findEntityById(id);
        checkOwner(entity, user);

        restaurantMapper.updateRestaurant(request, entity);

        entity = restaurantRepository.saveAndFlush(entity);

        log.info("Restaurante atualizado id={}", id);

        return restaurantMapper.mapToRestaurantResponse(entity);
    }

    @Transactional
    public RestaurantResponse updateAvailability(UUID id, UpdateAvailabilityRequest request, AuthenticatedUser user) {

        Restaurant entity = findEntityById(id);
        checkOwner(entity, user);

        entity.setOpen(request.open());

        entity = restaurantRepository.saveAndFlush(entity);

        log.info("Disponibilidade alterada id={} open={}", id, entity.isOpen());

        return restaurantMapper.mapToRestaurantResponse(entity);
    }

    private void checkOwner(Restaurant entity, AuthenticatedUser user) {
        if (user.isAdmin()) {
            return;
        }
        if (!user.isRestaurantOwner() || !user.id().equals(entity.getOwnerId())) {
            log.warn("Acesso negado ao restaurante id={} userId={}", entity.getId(), user.id());
            throw new ForbiddenException("Apenas o dono do restaurante ou ADMIN podem alterá-lo");
        }
    }

    private Restaurant findEntityById(UUID id) {
        return restaurantRepository.findById(id)
            .orElseThrow(() -> {
                log.warn("Restaurante não encontrado id={}", id);
                return new ResourceNotFoundException(
                    "Restaurante não encontrado: " + id
                );
            });
    }
}
