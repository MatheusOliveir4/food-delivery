package com.food_delivery.paymentservice.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.food_delivery.paymentservice.events.PaymentRequestedEvent;
import com.food_delivery.paymentservice.services.PaymentService;

import jakarta.validation.Valid;

/**
 * Consome {@code payment.requested.queue}. O tipo do JSON vem do parâmetro; JSON inválido ou
 * campo obrigatório faltando vai direto para a DLQ. A idempotência por orderId fica no serviço.
 */
@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentRequestedListener {
    private static final Logger log = LoggerFactory.getLogger(PaymentRequestedListener.class);

    private final PaymentService paymentService;

    public PaymentRequestedListener(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @RabbitListener(queues = PaymentTopology.PAYMENT_REQUESTED_QUEUE)
    public void onPaymentRequested(@Valid @Payload PaymentRequestedEvent event) {
        log.info("PaymentRequested recebido: orderId={} valor={} método={} eventId={}",
            event.orderId(), event.amount(), event.paymentMethod(), event.eventId());
        paymentService.processPaymentRequest(event);
    }
}
