package com.food_delivery.CatalogService.controllers;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.food_delivery.CatalogService.dtos.request.CreateCategoryRequest;
import com.food_delivery.CatalogService.dtos.request.UpdateCategoryRequest;
import com.food_delivery.CatalogService.dtos.response.CategoryResponse;
import com.food_delivery.CatalogService.services.CategoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/categories")
@Tag(name = "Categorias", description = "Categorias do cardápio de cada restaurante")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "Cadastrar categoria",
            description = "Cria uma categoria no cardápio de um restaurante")
    @ApiResponse(responseCode = "201", description = "Categoria criada")
    @ApiResponse(responseCode = "400", description = "Dados inválidos")
    @PostMapping
    public ResponseEntity<CategoryResponse> create(
            @Valid @RequestBody CreateCategoryRequest request) {
        CategoryResponse response = categoryService.save(request);
        return ResponseEntity.created(URI.create("/api/v1/categories/" + response.id()))
            .body(response);
    }

    @Operation(summary = "Buscar categoria por id")
    @ApiResponse(responseCode = "200", description = "Categoria encontrada")
    @ApiResponse(responseCode = "404", description = "Categoria não encontrada")
    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(categoryService.findById(id));
    }

    @Operation(summary = "Listar as categorias de um restaurante")
    @ApiResponse(responseCode = "200", description = "Lista de categorias")
    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<List<CategoryResponse>> findByRestaurant(
            @PathVariable UUID restaurantId) {
        return ResponseEntity.ok(categoryService.findByRestaurant(restaurantId));
    }

    @Operation(summary = "Atualizar categoria",
            description = "Altera os dados da categoria")
    @ApiResponse(responseCode = "200", description = "Categoria atualizada")
    @ApiResponse(responseCode = "400", description = "Dados inválidos")
    @ApiResponse(responseCode = "404", description = "Categoria não encontrada")
    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCategoryRequest request) {
        return ResponseEntity.ok(categoryService.update(id, request));
    }

    @Operation(summary = "Desativar categoria",
            description = "A categoria deixa de aparecer no cardápio")
    @ApiResponse(responseCode = "204", description = "Categoria desativada")
    @ApiResponse(responseCode = "404", description = "Categoria não encontrada")
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        categoryService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}