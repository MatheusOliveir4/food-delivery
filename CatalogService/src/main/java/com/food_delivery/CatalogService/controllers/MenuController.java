package com.food_delivery.CatalogService.controllers;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.food_delivery.CatalogService.dtos.response.MenuResponse;
import com.food_delivery.CatalogService.services.MenuService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/restaurants")
@Tag(name = "Cardápio", description = "Cardápio completo de um restaurante, organizado por categoria")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @Operation(summary = "Consultar o cardápio de um restaurante",
            description = "Devolve as categorias do restaurante com os produtos de cada uma")
    @ApiResponse(responseCode = "200", description = "Cardápio do restaurante")
    @GetMapping("/{restaurantId}/menu")
    public ResponseEntity<MenuResponse> getMenu(@PathVariable UUID restaurantId) {
        return ResponseEntity.ok(menuService.getRestaurantMenu(restaurantId));
    }
}