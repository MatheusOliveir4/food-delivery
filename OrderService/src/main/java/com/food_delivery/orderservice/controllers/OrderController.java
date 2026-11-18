package com.food_delivery.orderservice.controllers;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.food_delivery.orderservice.dtos.request.CheckoutRequest;
import com.food_delivery.orderservice.dtos.request.RejectOrderRequest;
import com.food_delivery.orderservice.dtos.request.UpdatePreparationRequest;
import com.food_delivery.orderservice.dtos.response.OrderResponse;
import com.food_delivery.orderservice.entities.enums.OrderStatus;
import com.food_delivery.orderservice.security.AuthenticatedUser;
import com.food_delivery.orderservice.services.OrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/orders")
@Tag(name = "Pedidos", description = "Checkout, consultas e ações do restaurante")
public class OrderController {
    @Autowired
    private OrderService orderService;

    @Operation(summary = "Checkout", description = "Valida restaurante e produtos, cria o pedido e publica PaymentRequested")
    @ApiResponse(responseCode = "201", description = "Pedido criado (PENDING_PAYMENT)")
    @ApiResponse(responseCode = "400", description = "Dados inválidos")
    @ApiResponse(responseCode = "401", description = "Sem token ou token inválido")
    @ApiResponse(responseCode = "403", description = "Não é CUSTOMER")
    @ApiResponse(responseCode = "422", description = "Carrinho vazio, restaurante fechado ou produto indisponível")
    @ApiResponse(responseCode = "503", description = "Catalog ou Restaurant indisponível")
    @PostMapping("/checkout")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderResponse> checkout(
        @AuthenticationPrincipal AuthenticatedUser user,
        @Valid @RequestBody CheckoutRequest request
    ) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(orderService.checkout(user.getId(), request));
    }

    @Operation(summary = "Buscar pedido", description = "Dono do pedido, dono do restaurante ou ADMIN")
    @ApiResponse(responseCode = "200", description = "Pedido encontrado")
    @ApiResponse(responseCode = "401", description = "Sem token ou token inválido")
    @ApiResponse(responseCode = "403", description = "Sem acesso ao pedido")
    @ApiResponse(responseCode = "404", description = "Pedido não encontrado")
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> findById(
        @AuthenticationPrincipal AuthenticatedUser user,
        @PathVariable UUID id
    ) {
        return ResponseEntity.ok(orderService.findById(user, id));
    }

    @Operation(summary = "Listar pedidos do cliente", description = "Só o próprio cliente ou um ADMIN")
    @ApiResponse(responseCode = "200", description = "Lista de pedidos")
    @ApiResponse(responseCode = "401", description = "Sem token ou token inválido")
    @ApiResponse(responseCode = "403", description = "Não é o cliente nem ADMIN")
    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasRole('ADMIN') or #customerId == authentication.principal.id")
    public ResponseEntity<List<OrderResponse>> findByCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(orderService.findByCustomer(customerId));
    }

    @Operation(summary = "Listar pedidos do restaurante", description = "Dono do restaurante ou ADMIN. Filtro opcional por status")
    @ApiResponse(responseCode = "200", description = "Lista de pedidos")
    @ApiResponse(responseCode = "401", description = "Sem token ou token inválido")
    @ApiResponse(responseCode = "403", description = "Não é dono do restaurante nem ADMIN")
    @ApiResponse(responseCode = "404", description = "Restaurante não encontrado")
    @GetMapping("/restaurant/{restaurantId}")
    @PreAuthorize("hasAnyRole('RESTAURANT_OWNER', 'ADMIN')")
    public ResponseEntity<List<OrderResponse>> findByRestaurant(
        @AuthenticationPrincipal AuthenticatedUser user,
        @PathVariable UUID restaurantId,
        @RequestParam(required = false) OrderStatus status
    ) {
        return ResponseEntity.ok(orderService.findByRestaurant(user, restaurantId, status));
    }

    @Operation(summary = "Aceitar pedido", description = "Só pedidos pagos (PENDING_RESTAURANT)")
    @ApiResponse(responseCode = "200", description = "Pedido aceito")
    @ApiResponse(responseCode = "401", description = "Sem token ou token inválido")
    @ApiResponse(responseCode = "403", description = "Não é dono do restaurante nem ADMIN")
    @ApiResponse(responseCode = "404", description = "Pedido não encontrado")
    @ApiResponse(responseCode = "409", description = "Status não permite a ação")
    @PatchMapping("/{id}/accept")
    @PreAuthorize("hasAnyRole('RESTAURANT_OWNER', 'ADMIN')")
    public ResponseEntity<OrderResponse> accept(
        @AuthenticationPrincipal AuthenticatedUser user,
        @PathVariable UUID id
    ) {
        return ResponseEntity.ok(orderService.accept(user, id));
    }

    @Operation(summary = "Rejeitar pedido", description = "Publica RefundRequested")
    @ApiResponse(responseCode = "200", description = "Pedido rejeitado")
    @ApiResponse(responseCode = "401", description = "Sem token ou token inválido")
    @ApiResponse(responseCode = "403", description = "Não é dono do restaurante nem ADMIN")
    @ApiResponse(responseCode = "404", description = "Pedido não encontrado")
    @ApiResponse(responseCode = "409", description = "Status não permite a ação")
    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('RESTAURANT_OWNER', 'ADMIN')")
    public ResponseEntity<OrderResponse> reject(
        @AuthenticationPrincipal AuthenticatedUser user,
        @PathVariable UUID id,
        @Valid @RequestBody(required = false) RejectOrderRequest request
    ) {
        return ResponseEntity.ok(orderService.reject(user, id, request));
    }

    @Operation(summary = "Atualizar preparação", description = "PREPARING depois de aceito, READY depois de PREPARING")
    @ApiResponse(responseCode = "200", description = "Status atualizado")
    @ApiResponse(responseCode = "400", description = "Dados inválidos")
    @ApiResponse(responseCode = "401", description = "Sem token ou token inválido")
    @ApiResponse(responseCode = "403", description = "Não é dono do restaurante nem ADMIN")
    @ApiResponse(responseCode = "404", description = "Pedido não encontrado")
    @ApiResponse(responseCode = "409", description = "Status não permite a ação")
    @PatchMapping("/{id}/preparation")
    @PreAuthorize("hasAnyRole('RESTAURANT_OWNER', 'ADMIN')")
    public ResponseEntity<OrderResponse> updatePreparation(
        @AuthenticationPrincipal AuthenticatedUser user,
        @PathVariable UUID id,
        @Valid @RequestBody UpdatePreparationRequest request
    ) {
        return ResponseEntity.ok(orderService.updatePreparation(user, id, request));
    }
}
