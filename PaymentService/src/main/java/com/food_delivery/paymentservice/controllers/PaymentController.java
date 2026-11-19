package com.food_delivery.paymentservice.controllers;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.food_delivery.paymentservice.dtos.request.RefundRequest;
import com.food_delivery.paymentservice.dtos.response.PaymentResponse;
import com.food_delivery.paymentservice.security.AuthenticatedUser;
import com.food_delivery.paymentservice.services.PaymentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@Tag(name = "Pagamentos", description = "Consulta e estorno de pagamentos simulados")
public class PaymentController {
    private final PaymentService paymentService;

    @Operation(summary = "Buscar pagamento", description = "Cliente dono do pagamento ou ADMIN")
    @ApiResponse(responseCode = "200", description = "Pagamento encontrado, com o histórico de transações")
    @ApiResponse(responseCode = "401", description = "Sem token ou token inválido")
    @ApiResponse(responseCode = "403", description = "Não é o cliente nem ADMIN")
    @ApiResponse(responseCode = "404", description = "Pagamento não encontrado")
    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> findById(
        @AuthenticationPrincipal AuthenticatedUser user,
        @PathVariable UUID id
    ) {
        return ResponseEntity.ok(paymentService.findById(user, id));
    }

    @Operation(summary = "Buscar pagamento do pedido", description = "Cliente dono do pagamento ou ADMIN")
    @ApiResponse(responseCode = "200", description = "Pagamento encontrado, com o histórico de transações")
    @ApiResponse(responseCode = "401", description = "Sem token ou token inválido")
    @ApiResponse(responseCode = "403", description = "Não é o cliente nem ADMIN")
    @ApiResponse(responseCode = "404", description = "O pedido não tem pagamento")
    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentResponse> findByOrderId(
        @AuthenticationPrincipal AuthenticatedUser user,
        @PathVariable UUID orderId
    ) {
        return ResponseEntity.ok(paymentService.findByOrderId(user, orderId));
    }

    @Operation(summary = "Estornar pagamento",
        description = "Só ADMIN. Estorna um pagamento APPROVED e publica PaymentRefunded")
    @ApiResponse(responseCode = "200", description = "Pagamento estornado (REFUNDED)")
    @ApiResponse(responseCode = "401", description = "Sem token ou token inválido")
    @ApiResponse(responseCode = "403", description = "Não é ADMIN")
    @ApiResponse(responseCode = "404", description = "Pagamento não encontrado")
    @ApiResponse(responseCode = "409", description = "Pagamento recusado ou já estornado")
    @PostMapping("/{id}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaymentResponse> refund(
        @PathVariable UUID id,
        @Valid @RequestBody(required = false) RefundRequest request
    ) {
        return ResponseEntity.ok(paymentService.refund(id, request != null ? request.reason() : null));
    }
}
