package com.food_delivery.CatalogService.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

import com.food_delivery.CatalogService.dtos.request.CreateProductRequest;
import com.food_delivery.CatalogService.dtos.request.UpdateProductRequest;
import com.food_delivery.CatalogService.dtos.response.ProductResponse;
import com.food_delivery.CatalogService.entities.Product;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProductMapper {

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    ProductResponse mapToProductResponse(Product product);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "restaurantId", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "available", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Product mapToProduct(CreateProductRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "restaurantId", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "available", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateProduct(UpdateProductRequest request, @MappingTarget Product product);
}
