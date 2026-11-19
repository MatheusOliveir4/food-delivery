package com.food_delivery.paymentservice.messaging;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.food_delivery.paymentservice.events.RefundRequestedEvent;
import com.food_delivery.paymentservice.exceptions.InvalidPaymentStateException;
import com.food_delivery.paymentservice.services.PaymentService;

import jakarta.validation.Valid;

/**
 * Consome {@code payment.refund-requested.queue} e chama o mesmo {@link PaymentService#refund}
 * do endpoint REST. Pagamento inexistente, recusado ou já estornado é descartado com log
 * (sem exceção, então não vai para a DLQ).
 */
@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class RefundRequestedListener {
    private static final Logger log = LoggerFactory.getLogger(RefundRequestedListener.class);

    private final PaymentService paymentService;

    public RefundRequestedListener(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @RabbitListener(queues = PaymentTopology.REFUND_REQUESTED_QUEUE)
    public void onRefundRequested(@Valid @Payload RefundRequestedEvent event) {
        log.info("RefundRequested recebido: orderId={} eventId={} motivo={}",
            event.orderId(), event.eventId(), event.reason());

        Optional<UUID> paymentId = paymentService.findPaymentIdByOrderId(event.orderId());
        if (paymentId.isEmpty()) {
            log.warn("RefundRequested descartado: não existe pagamento para o pedido {} (eventId={})",
                event.orderId(), event.eventId());
            return;
        }

        try {
            paymentService.refund(paymentId.get(), event.reason());
        } catch (InvalidPaymentStateException e) {
            log.warn("RefundRequested descartado para o pedido {} (eventId={}): {}",
                event.orderId(), event.eventId(), e.getMessage());
        }
    }
}
