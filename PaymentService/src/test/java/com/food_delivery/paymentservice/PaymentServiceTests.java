package com.food_delivery.paymentservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;

import com.food_delivery.paymentservice.entities.Payment;
import com.food_delivery.paymentservice.entities.PaymentTransaction;
import com.food_delivery.paymentservice.entities.enums.PaymentMethod;
import com.food_delivery.paymentservice.entities.enums.PaymentStatus;
import com.food_delivery.paymentservice.entities.enums.TransactionStatus;
import com.food_delivery.paymentservice.entities.enums.TransactionType;
import com.food_delivery.paymentservice.events.PaymentApprovedEvent;
import com.food_delivery.paymentservice.events.PaymentEventPublisher;
import com.food_delivery.paymentservice.events.PaymentRefundedEvent;
import com.food_delivery.paymentservice.events.PaymentRequestedEvent;
import com.food_delivery.paymentservice.exceptions.InvalidPaymentStateException;
import com.food_delivery.paymentservice.exceptions.ResourceNotFoundException;
import com.food_delivery.paymentservice.mappers.PaymentMapper;
import com.food_delivery.paymentservice.repositories.PaymentRepository;
import com.food_delivery.paymentservice.services.PaymentService;
import com.food_delivery.paymentservice.simulation.PaymentSimulator;

/** Regras de estorno e idempotência, sem Spring e sem banco. */
class PaymentServiceTests {
    private final PaymentRepository repository = mock(PaymentRepository.class);
    private final PaymentSimulator simulator = mock(PaymentSimulator.class);
    private final PaymentEventPublisher publisher = mock(PaymentEventPublisher.class);
    private final PaymentService service =
        new PaymentService(repository, simulator, publisher, Mappers.getMapper(PaymentMapper.class));

    @BeforeEach
    void saveReturnsTheEntity() {
        when(repository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void approvedPaymentIsRefunded() {
        Payment payment = payment(PaymentStatus.APPROVED);
        when(repository.findById(payment.getId())).thenReturn(Optional.of(payment));

        var response = service.refund(payment.getId(), "Restaurante rejeitou");

        assertThat(response.status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getTransactions()).hasSize(2);
        PaymentTransaction refund = payment.getTransactions().get(1);
        assertThat(refund.getType()).isEqualTo(TransactionType.REFUND);
        assertThat(refund.getStatus()).isEqualTo(TransactionStatus.SUCCEEDED);
        assertThat(refund.getAmount()).isEqualByComparingTo(payment.getAmount());
        assertThat(refund.getReason()).isEqualTo("Restaurante rejeitou");

        ArgumentCaptor<PaymentRefundedEvent> event = ArgumentCaptor.forClass(PaymentRefundedEvent.class);
        verify(publisher).publishPaymentRefunded(event.capture());
        assertThat(event.getValue().orderId()).isEqualTo(payment.getOrderId());
        assertThat(event.getValue().amount()).isEqualByComparingTo("42.00");
        assertThat(event.getValue().eventId()).isNotNull();
    }

    @Test
    void refundWithoutReasonUsesDefault() {
        Payment payment = payment(PaymentStatus.APPROVED);
        when(repository.findById(payment.getId())).thenReturn(Optional.of(payment));

        service.refund(payment.getId(), "  ");

        assertThat(payment.getTransactions().get(1).getReason()).isEqualTo("Estorno solicitado");
    }

    @Test
    void alreadyRefundedPaymentCannotBeRefundedAgain() {
        Payment payment = payment(PaymentStatus.REFUNDED);
        when(repository.findById(payment.getId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.refund(payment.getId(), "x"))
            .isInstanceOf(InvalidPaymentStateException.class);
        verify(publisher, never()).publishPaymentRefunded(any());
        assertThat(payment.getTransactions()).hasSize(1);
    }

    @Test
    void rejectedPaymentCannotBeRefunded() {
        Payment payment = payment(PaymentStatus.REJECTED);
        when(repository.findById(payment.getId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.refund(payment.getId(), "x"))
            .isInstanceOf(InvalidPaymentStateException.class);
        verify(publisher, never()).publishPaymentRefunded(any());
    }

    @Test
    void unknownPaymentIsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refund(id, "x")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void repeatedPaymentRequestDoesNotChargeAgainAndResendsApproval() {
        Payment payment = payment(PaymentStatus.APPROVED);
        when(repository.findByOrderId(payment.getOrderId())).thenReturn(Optional.of(payment));

        service.processPaymentRequest(new PaymentRequestedEvent(UUID.randomUUID(), payment.getOrderId(), 7L,
            new BigDecimal("42.00"), PaymentMethod.PIX, LocalDateTime.now(), null));

        verify(simulator, never()).decide(any(), any());
        verify(repository, never()).saveAndFlush(any());
        ArgumentCaptor<PaymentApprovedEvent> event = ArgumentCaptor.forClass(PaymentApprovedEvent.class);
        verify(publisher).publishPaymentApproved(event.capture());
        assertThat(event.getValue().paymentId()).isEqualTo(payment.getId());
        assertThat(event.getValue().transactionId()).isEqualTo(payment.getTransactions().get(0).getId());
    }

    private static Payment payment(PaymentStatus status) {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setOrderId(UUID.randomUUID());
        payment.setCustomerId(7L);
        payment.setAmount(new BigDecimal("42.00"));
        payment.setMethod(PaymentMethod.PIX);
        payment.setStatus(status);

        PaymentTransaction charge = new PaymentTransaction(TransactionType.CHARGE,
            status == PaymentStatus.REJECTED ? TransactionStatus.FAILED : TransactionStatus.SUCCEEDED,
            payment.getAmount(), null);
        charge.setId(UUID.randomUUID());
        charge.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        payment.addTransaction(charge);
        return payment;
    }
}
