package com.food_delivery.CatalogService.controllers;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.food_delivery.CatalogService.dtos.response.MenuResponse;
import com.food_delivery.CatalogService.services.MenuService;

@RestController
@RequestMapping("/api/v1/restaurants")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping("/{restaurantId}/menu")
    public ResponseEntity<MenuResponse> getMenu(@PathVariable UUID restaurantId) {
        return ResponseEntity.ok(menuService.getRestaurantMenu(restaurantId));
    }
}
