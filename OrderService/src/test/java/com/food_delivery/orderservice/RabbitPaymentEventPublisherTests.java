package com.food_delivery.orderservice;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.food_delivery.orderservice.entities.enums.PaymentMethod;
import com.food_delivery.orderservice.events.PaymentRequestedEvent;
import com.food_delivery.orderservice.events.RabbitPaymentEventPublisher;
import com.food_delivery.orderservice.events.RefundRequestedEvent;
import com.food_delivery.orderservice.messaging.PaymentTopology;

class RabbitPaymentEventPublisherTests {
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final RabbitPaymentEventPublisher publisher = new RabbitPaymentEventPublisher(rabbitTemplate);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void insideTransactionPublishesOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        PaymentRequestedEvent event = PaymentRequestedEvent.of(UUID.randomUUID(), 1L, BigDecimal.TEN, PaymentMethod.PIX);

        publisher.publishPaymentRequested(event);
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class),
            any(MessagePostProcessor.class), any(CorrelationData.class));

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(rabbitTemplate).convertAndSend(eq(PaymentTopology.EXCHANGE), eq(PaymentTopology.PAYMENT_REQUESTED),
            eq(event), any(MessagePostProcessor.class), any(CorrelationData.class));
    }

    @Test
    void rolledBackTransactionPublishesNothing() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.publishRefundRequested(RefundRequestedEvent.of(UUID.randomUUID(), 1L, BigDecimal.TEN, "motivo"));
        TransactionSynchronizationManager.getSynchronizations()
            .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class),
            any(MessagePostProcessor.class), any(CorrelationData.class));
    }

    @Test
    void brokerFailureAfterCommitIsLoggedAndDoesNotPropagate() {
        doThrow(new AmqpConnectException(new RuntimeException("broker fora")))
            .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class),
                any(MessagePostProcessor.class), any(CorrelationData.class));

        // Sem transação ativa: publica na hora. A falha não pode estourar no checkout já gravado.
        publisher.publishPaymentRequested(PaymentRequestedEvent.of(UUID.randomUUID(), 1L, BigDecimal.TEN, PaymentMethod.PIX));
    }
}
