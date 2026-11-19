package com.food_delivery.CatalogService.controllers;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.food_delivery.CatalogService.dtos.request.CreateProductRequest;
import com.food_delivery.CatalogService.dtos.request.UpdateProductAvailabilityRequest;
import com.food_delivery.CatalogService.dtos.request.UpdateProductRequest;
import com.food_delivery.CatalogService.dtos.response.ProductResponse;
import com.food_delivery.CatalogService.services.ProductService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/products")
@Tag(name = "Produtos", description = "Cardápio: cadastro, consulta e disponibilidade dos produtos")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "Cadastrar produto",
            description = "Cria um produto no cardápio de um restaurante")
    @ApiResponse(responseCode = "201", description = "Produto criado")
    @ApiResponse(responseCode = "400", description = "Dados inválidos")
    @PostMapping
    public ResponseEntity<ProductResponse> create(
            @Valid @RequestBody CreateProductRequest request) {
        ProductResponse response = productService.save(request);
        return ResponseEntity.created(URI.create("/api/v1/products/" + response.id()))
            .body(response);
    }

    @Operation(summary = "Buscar produto por id")
    @ApiResponse(responseCode = "200", description = "Produto encontrado")
    @ApiResponse(responseCode = "404", description = "Produto não encontrado")
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    @Operation(summary = "Listar o cardápio de um restaurante",
            description = "Devolve todos os produtos do restaurante, disponíveis ou não")
    @ApiResponse(responseCode = "200", description = "Lista de produtos")
    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<List<ProductResponse>> findByRestaurant(
            @PathVariable UUID restaurantId) {
        return ResponseEntity.ok(productService.findByRestaurant(restaurantId));
    }

    @Operation(summary = "Listar os produtos disponíveis de um restaurante",
            description = "Devolve só os produtos que estão disponíveis para pedido")
    @ApiResponse(responseCode = "200", description = "Lista de produtos disponíveis")
    @GetMapping("/restaurant/{restaurantId}/available")
    public ResponseEntity<List<ProductResponse>> findAvailableByRestaurant(
            @PathVariable UUID restaurantId) {
        return ResponseEntity.ok(productService.findAvailableByRestaurant(restaurantId));
    }

    @Operation(summary = "Atualizar produto",
            description = "Altera os dados do produto, como nome e preço")
    @ApiResponse(responseCode = "200", description = "Produto atualizado")
    @ApiResponse(responseCode = "400", description = "Dados inválidos")
    @ApiResponse(responseCode = "404", description = "Produto não encontrado")
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest request) {
        return ResponseEntity.ok(productService.update(id, request));
    }

    @Operation(summary = "Alterar disponibilidade do produto",
            description = "Marca o produto como disponível ou indisponível")
    @ApiResponse(responseCode = "200", description = "Disponibilidade atualizada")
    @ApiResponse(responseCode = "400", description = "Dados inválidos")
    @ApiResponse(responseCode = "404", description = "Produto não encontrado")
    @PatchMapping("/{id}/availability")
    public ResponseEntity<ProductResponse> updateAvailability(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductAvailabilityRequest request) {
        return ResponseEntity.ok(productService.updateAvailability(id, request));
    }

    @Operation(summary = "Desativar produto",
            description = "Exclusão lógica: o produto permanece no banco, mas fica indisponível")
    @ApiResponse(responseCode = "204", description = "Produto desativado")
    @ApiResponse(responseCode = "404", description = "Produto não encontrado")
    // Exclusão lógica: o produto permanece no banco, mas fica indisponível.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        productService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}