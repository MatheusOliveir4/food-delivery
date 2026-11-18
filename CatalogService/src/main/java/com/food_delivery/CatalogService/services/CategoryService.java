package com.food_delivery.CatalogService.services;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.food_delivery.CatalogService.dtos.request.CreateCategoryRequest;
import com.food_delivery.CatalogService.dtos.request.UpdateCategoryRequest;
import com.food_delivery.CatalogService.dtos.response.CategoryResponse;
import com.food_delivery.CatalogService.entities.Category;
import com.food_delivery.CatalogService.exceptions.BusinessException;
import com.food_delivery.CatalogService.exceptions.ResourceNotFoundException;
import com.food_delivery.CatalogService.mappers.CategoryMapper;
import com.food_delivery.CatalogService.repositories.CategoryRepository;
import com.food_delivery.CatalogService.repositories.ProductRepository;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper categoryMapper;

    public CategoryService(
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            CategoryMapper categoryMapper) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.categoryMapper = categoryMapper;
    }

    @Transactional(readOnly = true)
    public CategoryResponse findById(UUID id) {
        return categoryMapper.mapToCategoryResponse(getCategory(id));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> findByRestaurant(UUID restaurantId) {
        return categoryRepository.findByRestaurantIdAndActiveTrueOrderByNameAsc(restaurantId)
            .stream()
            .map(categoryMapper::mapToCategoryResponse)
            .toList();
    }

    @Transactional
    public CategoryResponse save(CreateCategoryRequest request) {
        String normalizedName = request.name().strip();
        if (categoryRepository.existsByRestaurantIdAndNameIgnoreCase(
                request.restaurantId(), normalizedName)) {
            throw new BusinessException("Já existe uma categoria com esse nome para o restaurante.");
        }

        Category category = categoryMapper.mapToCategory(request);
        category.setName(normalizedName);
        category.setActive(true);
        Category saved = categoryRepository.save(category);
        return categoryMapper.mapToCategoryResponse(saved);
    }

    @Transactional
    public CategoryResponse update(UUID id, UpdateCategoryRequest request) {
        Category category = getCategory(id);
        if (!category.isActive()) {
            throw new BusinessException("Não é possível atualizar uma categoria inativa.");
        }

        String normalizedName = request.name().strip();
        if (categoryRepository.existsByRestaurantIdAndNameIgnoreCaseAndIdNot(
                category.getRestaurantId(), normalizedName, id)) {
            throw new BusinessException("Já existe uma categoria com esse nome para o restaurante.");
        }

        categoryMapper.updateCategory(request, category);
        category.setName(normalizedName);
        return categoryMapper.mapToCategoryResponse(categoryRepository.save(category));
    }

    @Transactional
    public void deactivate(UUID id) {
        Category category = getCategory(id);
        if (productRepository.existsByCategory_Id(id)) {
            throw new BusinessException(
                "Não é possível desativar a categoria enquanto houver produtos vinculados a ela. " +
                "Desative ou mova os produtos primeiro."
            );
        }
        category.setActive(false);
        categoryRepository.save(category);
    }

    private Category getCategory(UUID id) {
        return categoryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada: " + id));
    }
}
