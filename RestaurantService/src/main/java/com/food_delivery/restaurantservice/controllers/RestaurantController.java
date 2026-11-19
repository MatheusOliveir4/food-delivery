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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/restaurants")
@Tag(name = "Restaurantes", description = "Cadastro, consulta e disponibilidade dos restaurantes")
public class RestaurantController {

    @Autowired
    private RestaurantService restaurantService;

    @Operation(summary = "Cadastrar restaurante",
            description = "Cria um restaurante e associa o proprietário")
    @ApiResponse(responseCode = "201", description = "Restaurante criado")
    @ApiResponse(responseCode = "400", description = "Dados inválidos")
    @PostMapping
    public ResponseEntity<RestaurantResponse> create(@Valid @RequestBody CreateRestaurantRequest request) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(restaurantService.save(request));
    }

    @Operation(summary = "Listar restaurantes",
            description = "Consulta os restaurantes, com filtros opcionais por situação (aberto ou fechado) e por proprietário")
    @ApiResponse(responseCode = "200", description = "Lista de restaurantes")
    @GetMapping
    public ResponseEntity<List<RestaurantResponse>> findAll(
        @Parameter(description = "Filtra por restaurantes abertos (true) ou fechados (false)")
        @RequestParam(required = false) Boolean open,
        @Parameter(description = "Filtra pelos restaurantes de um proprietário")
        @RequestParam(required = false) Long ownerId
    ) {
        return ResponseEntity.ok(restaurantService.findAll(open, ownerId));
    }

    @Operation(summary = "Buscar restaurante por id")
    @ApiResponse(responseCode = "200", description = "Restaurante encontrado")
    @ApiResponse(responseCode = "404", description = "Restaurante não encontrado")
    @GetMapping("/{id}")
    public ResponseEntity<RestaurantResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(restaurantService.findById(id));
    }

    @Operation(summary = "Atualizar restaurante",
            description = "Altera os dados do restaurante")
    @ApiResponse(responseCode = "200", description = "Restaurante atualizado")
    @ApiResponse(responseCode = "400", description = "Dados inválidos")
    @ApiResponse(responseCode = "404", description = "Restaurante não encontrado")
    @PutMapping("/{id}")
    public ResponseEntity<RestaurantResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateRestaurantRequest request
    ) {
        return ResponseEntity.ok(restaurantService.update(id, request));
    }

    @Operation(summary = "Abrir ou fechar o restaurante",
            description = "Controla se o restaurante está aberto ou fechado para pedidos")
    @ApiResponse(responseCode = "200", description = "Disponibilidade atualizada")
    @ApiResponse(responseCode = "400", description = "Dados inválidos")
    @ApiResponse(responseCode = "404", description = "Restaurante não encontrado")
    @PatchMapping("/{id}/availability")
    public ResponseEntity<RestaurantResponse> updateAvailability(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateAvailabilityRequest request
    ) {
        return ResponseEntity.ok(restaurantService.updateAvailability(id, request));
    }
}