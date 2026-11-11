package com.food_delivery.CatalogService.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.MappingConstants;

import com.food_delivery.CatalogService.dtos.request.CreateCategoryRequest;
import com.food_delivery.CatalogService.dtos.request.UpdateCategoryRequest;
import com.food_delivery.CatalogService.dtos.response.CategoryResponse;
import com.food_delivery.CatalogService.entities.Category;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CategoryMapper {

    CategoryResponse mapToCategoryResponse(Category category);

    Category mapToCategory(CreateCategoryRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "restaurantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateCategory(
        UpdateCategoryRequest request,
        @MappingTarget Category category
    );
}