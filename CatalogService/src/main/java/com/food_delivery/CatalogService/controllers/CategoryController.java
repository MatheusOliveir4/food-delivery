package com.food_delivery.CatalogService.controllers;

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
import org.springframework.web.bind.annotation.RestController;

import com.food_delivery.CatalogService.dtos.request.CreateCategoryRequest;
import com.food_delivery.CatalogService.dtos.request.UpdateCategoryRequest;
import com.food_delivery.CatalogService.dtos.response.CategoryResponse;
import com.food_delivery.CatalogService.services.CategoryService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController 
@RequestMapping ("/categories")
public class CategoryController {

    @Autowired 
    private CategoryService categoryService;

    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(categoryService.findById(id));
    }

    // @GetMapping("/restaurant/{restaurantId}")
    // public ResponseEntity<List<CategoryResponse>> findByRestaurant(@PathVariable UUID restaurantId) {
    //     return ResponseEntity.ok(categoryService.findByRestaurant(restaurantId));
    // }

    @PostMapping 
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(categoryService.save(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateCategoryRequest request
    ) {
        return ResponseEntity.ok(categoryService.update(id, request));
    }

    @PatchMapping("/{id}/delete")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        categoryService.delete(id);

        return ResponseEntity.noContent().build();
    }
}
