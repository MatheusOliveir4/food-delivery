package com.food_delivery.restaurantservice.controllers;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.food_delivery.restaurantservice.dtos.request.CreateRestaurantRequest;
import com.food_delivery.restaurantservice.dtos.request.UpdateAvailabilityRequest;
import com.food_delivery.restaurantservice.dtos.request.UpdateRestaurantRequest;
import com.food_delivery.restaurantservice.dtos.response.RestaurantResponse;
import com.food_delivery.restaurantservice.services.RestaurantService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/restaurants")
public class RestaurantController {

    @Autowired
    private RestaurantService restaurantService;

    @PostMapping
    public ResponseEntity<RestaurantResponse> create(@Valid @RequestBody CreateRestaurantRequest request) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(restaurantService.save(request));
    }

    @GetMapping
    public ResponseEntity<List<RestaurantResponse>> findAll(
        @RequestParam(required = false) Boolean open,
        @RequestParam(required = false) Long ownerId
    ) {
        return ResponseEntity.ok(restaurantService.findAll(open, ownerId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RestaurantResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(restaurantService.findById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RestaurantResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateRestaurantRequest request
    ) {
        return ResponseEntity.ok(restaurantService.update(id, request));
    }

    @PatchMapping("/{id}/availability")
    public ResponseEntity<RestaurantResponse> updateAvailability(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateAvailabilityRequest request
    ) {
        return ResponseEntity.ok(restaurantService.updateAvailability(id, request));
    }
}
