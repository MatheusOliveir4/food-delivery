package com.food_delivery.orderservice.controllers;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.food_delivery.orderservice.dtos.request.AddCartItemRequest;
import com.food_delivery.orderservice.dtos.request.UpdateCartItemRequest;
import com.food_delivery.orderservice.dtos.response.CartResponse;
import com.food_delivery.orderservice.security.AuthenticatedUser;
import com.food_delivery.orderservice.services.CartService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/cart")
@Tag(name = "Carrinho", description = "Itens do carrinho do cliente")
public class CartController {
    @Autowired
    private CartService cartService;

    @Operation(summary = "Adicionar item", description = "Consulta o produto no Catalog Service. O cliente vem do token")
    @ApiResponse(responseCode = "201", description = "Item adicionado")
    @ApiResponse(responseCode = "400", description = "Dados inválidos")
    @ApiResponse(responseCode = "401", description = "Sem token ou token inválido")
    @ApiResponse(responseCode = "403", description = "Não é CUSTOMER")
    @ApiResponse(responseCode = "404", description = "Produto não encontrado")
    @ApiResponse(responseCode = "422", description = "Produto indisponível ou de outro restaurante")
    @PostMapping("/items")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CartResponse> addItem(
        @AuthenticationPrincipal AuthenticatedUser user,
        @Valid @RequestBody AddCartItemRequest request
    ) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(cartService.addItem(user.getId(), request));
    }

    @Operation(summary = "Buscar carrinho", description = "Só o próprio cliente ou um ADMIN")
    @ApiResponse(responseCode = "200", description = "Carrinho (vazio se não existir)")
    @ApiResponse(responseCode = "401", description = "Sem token ou token inválido")
    @ApiResponse(responseCode = "403", description = "Não é o dono do carrinho nem ADMIN")
    @GetMapping("/{customerId}")
    @PreAuthorize("hasRole('ADMIN') or #customerId == authentication.principal.id")
    public ResponseEntity<CartResponse> findByCustomerId(@PathVariable Long customerId) {
        return ResponseEntity.ok(cartService.findByCustomerId(customerId));
    }

    @Operation(summary = "Alterar quantidade de um item")
    @ApiResponse(responseCode = "200", description = "Carrinho atualizado")
    @ApiResponse(responseCode = "400", description = "Dados inválidos")
    @ApiResponse(responseCode = "401", description = "Sem token ou token inválido")
    @ApiResponse(responseCode = "403", description = "Não é CUSTOMER")
    @ApiResponse(responseCode = "404", description = "Item não encontrado no carrinho")
    @PutMapping("/items/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CartResponse> updateItem(
        @AuthenticationPrincipal AuthenticatedUser user,
        @PathVariable UUID id,
        @Valid @RequestBody UpdateCartItemRequest request
    ) {
        return ResponseEntity.ok(cartService.updateItem(user.getId(), id, request));
    }

    @Operation(summary = "Remover item")
    @ApiResponse(responseCode = "204", description = "Item removido")
    @ApiResponse(responseCode = "401", description = "Sem token ou token inválido")
    @ApiResponse(responseCode = "403", description = "Não é CUSTOMER")
    @ApiResponse(responseCode = "404", description = "Item não encontrado no carrinho")
    @DeleteMapping("/items/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Void> removeItem(
        @AuthenticationPrincipal AuthenticatedUser user,
        @PathVariable UUID id
    ) {
        cartService.removeItem(user.getId(), id);

        return ResponseEntity.noContent().build();
    }
}
