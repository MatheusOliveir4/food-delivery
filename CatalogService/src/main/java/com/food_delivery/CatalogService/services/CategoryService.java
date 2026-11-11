package com.food_delivery.CatalogService.services;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.food_delivery.CatalogService.dtos.request.CreateCategoryRequest;
import com.food_delivery.CatalogService.dtos.request.UpdateCategoryRequest;
import com.food_delivery.CatalogService.dtos.response.CategoryResponse;
import com.food_delivery.CatalogService.entities.Category;
// import com.food_delivery.CatalogService.exceptions.BusinessException;
// import com.food_delivery.CatalogService.exceptions.ResourceNotFoundException;
import com.food_delivery.CatalogService.mappers.CategoryMapper;
import com.food_delivery.CatalogService.repositories.CategoryRepository;
// import com.food_delivery.CatalogService.repositories.ProductRepository;

@Service
public class CategoryService {

    @Autowired 
    private CategoryRepository categoryRepository;
    // @Autowired
    // private ProductRepository productRepository;
    @Autowired
    private CategoryMapper categoryMapper;
  

    @Transactional(readOnly = true)
    public CategoryResponse findById(UUID id) {

        Category entity = categoryRepository.findById(id)
            .orElseThrow(() ->
                new Error(
                    "Categoria não encontrada: " + id
                )
            );

        return categoryMapper.mapToCategoryResponse(entity);
    }

    @Transactional
    public CategoryResponse save(CreateCategoryRequest request) {

        Category entity = categoryMapper.mapToCategory(request);

        entity = categoryRepository.save(entity);

        return categoryMapper.mapToCategoryResponse(entity);
    }

    @Transactional
    public CategoryResponse update(UUID id, UpdateCategoryRequest request) {

        Category entity = categoryRepository.findById(id)
            .orElseThrow(() ->
                new Error(
                    "Categoria não encontrada: " + id
                )
            );

        categoryMapper.updateCategory(request, entity);

        entity = categoryRepository.save(entity);

        return categoryMapper.mapToCategoryResponse(entity);
    }

    @Transactional
    public void delete(UUID id) {

        Category entity = categoryRepository.findById(id)
            .orElseThrow(() ->
                new Error(
                    "Categoria não encontrada: " + id
                )
            );

        // if (productRepository.existsByCategoryId(id)) {
        //     throw new BusinessException(
        //         "Não é possível excluir uma categoria que possui produtos vinculados."
        //     );
        // }

        categoryRepository.save(entity);
    }
}