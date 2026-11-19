package com.food_delivery.CatalogService.services;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.food_delivery.CatalogService.dtos.request.CreateProductRequest;
import com.food_delivery.CatalogService.dtos.request.UpdateProductAvailabilityRequest;
import com.food_delivery.CatalogService.dtos.request.UpdateProductRequest;
import com.food_delivery.CatalogService.dtos.response.ProductResponse;
import com.food_delivery.CatalogService.entities.Category;
import com.food_delivery.CatalogService.entities.Product;
import com.food_delivery.CatalogService.exceptions.BusinessException;
import com.food_delivery.CatalogService.exceptions.ResourceNotFoundException;
import com.food_delivery.CatalogService.mappers.ProductMapper;
import com.food_delivery.CatalogService.repositories.CategoryRepository;
import com.food_delivery.CatalogService.repositories.ProductRepository;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productMapper = productMapper;
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(UUID id) {
        return productMapper.mapToProductResponse(getProduct(id));
    }

    // Visão de gerenciamento: inclui produtos indisponíveis.
    @Transactional(readOnly = true)
    public List<ProductResponse> findByRestaurant(UUID restaurantId) {
        return productRepository.findByRestaurantIdOrderByNameAsc(restaurantId)
            .stream()
            .map(productMapper::mapToProductResponse)
            .toList();
    }

    // Visão pública: somente produtos disponíveis.
    @Transactional(readOnly = true)
    public List<ProductResponse> findAvailableByRestaurant(UUID restaurantId) {
        return productRepository
            .findByRestaurantIdAndAvailableTrueOrderByNameAsc(restaurantId)
            .stream()
            .map(productMapper::mapToProductResponse)
            .toList();
    }

    @Transactional
    public ProductResponse save(CreateProductRequest request) {
        Category category = getActiveCategory(request.categoryId());

        Product product = productMapper.mapToProduct(request);
        product.setCategory(category);
        product.setRestaurantId(category.getRestaurantId());
        product.setAvailable(true);

        return productMapper.mapToProductResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(UUID id, UpdateProductRequest request) {
        Product product = getProduct(id);
        Category newCategory = getActiveCategory(request.categoryId());

        if (!product.getRestaurantId().equals(newCategory.getRestaurantId())) {
            throw new BusinessException(
                "O produto só pode ser associado a uma categoria do mesmo restaurante."
            );
        }

        productMapper.updateProduct(request, product);
        product.setCategory(newCategory);

        return productMapper.mapToProductResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse updateAvailability(
            UUID id, UpdateProductAvailabilityRequest request) {
        Product product = getProduct(id);

        if (Boolean.TRUE.equals(request.available()) && !product.getCategory().isActive()) {
            throw new BusinessException(
                "Não é possível disponibilizar um produto cuja categoria está inativa."
            );
        }

        product.setAvailable(request.available());
        return productMapper.mapToProductResponse(productRepository.save(product));
    }

    @Transactional
    public void deactivate(UUID id) {
        Product product = getProduct(id);
        product.setAvailable(false);
        productRepository.save(product);
    }

    private Product getProduct(UUID id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + id));
    }

    private Category getActiveCategory(UUID categoryId) {
        Category category = categoryRepository.findById(categoryId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Categoria não encontrada: " + categoryId
            ));

        if (!category.isActive()) {
            throw new BusinessException("A categoria informada está inativa.");
        }
        return category;
    }
}
