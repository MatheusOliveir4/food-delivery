package com.food_delivery.CatalogService.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.food_delivery.CatalogService.dtos.response.CategoryMenuResponse;
import com.food_delivery.CatalogService.dtos.response.MenuResponse;
import com.food_delivery.CatalogService.dtos.response.ProductResponse;
import com.food_delivery.CatalogService.entities.Category;
import com.food_delivery.CatalogService.entities.Product;
import com.food_delivery.CatalogService.mappers.ProductMapper;
import com.food_delivery.CatalogService.repositories.CategoryRepository;
import com.food_delivery.CatalogService.repositories.ProductRepository;

@Service
public class MenuService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    public MenuService(
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            ProductMapper productMapper) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.productMapper = productMapper;
    }

    @Transactional(readOnly = true)
    public MenuResponse getRestaurantMenu(UUID restaurantId) {
        List<Category> categories = categoryRepository
            .findByRestaurantIdAndActiveTrueOrderByNameAsc(restaurantId);

        List<Product> products = productRepository
            .findByRestaurantIdAndAvailableTrueAndCategory_ActiveTrueOrderByNameAsc(restaurantId);

        Map<UUID, List<ProductResponse>> productsByCategory = products.stream()
            .map(productMapper::mapToProductResponse)
            .collect(Collectors.groupingBy(
                ProductResponse::categoryId,
                LinkedHashMap::new,
                Collectors.toList()
            ));

        List<CategoryMenuResponse> menuCategories = new ArrayList<>();
        for (Category category : categories) {
            menuCategories.add(new CategoryMenuResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                productsByCategory.getOrDefault(category.getId(), List.of())
            ));
        }

        return new MenuResponse(restaurantId, menuCategories);
    }
}
