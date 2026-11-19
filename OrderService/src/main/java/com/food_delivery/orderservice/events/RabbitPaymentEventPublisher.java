package com.food_delivery.orderservice.events;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.food_delivery.orderservice.config.CorrelationIdFilter;
import com.food_delivery.orderservice.messaging.AfterCommit;
import com.food_delivery.orderservice.messaging.PaymentTopology;

import jakarta.annotation.PostConstruct;

/**
 * Publica os eventos de pagamento no RabbitMQ depois do commit da transação que os gerou.
 *
 * <p>Limitação conhecida: sem Transactional Outbox, se o broker falhar entre o commit e o envio,
 * o evento se perde (o pedido fica gravado e o erro aparece no log).
 */
@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitPaymentEventPublisher implements PaymentEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(RabbitPaymentEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public RabbitPaymentEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostConstruct
    void warnAboutOutbox() {
        log.info("Eventos de pagamento são publicados no RabbitMQ após o commit. "
            + "Limitação conhecida: sem Outbox, uma falha do broker entre o commit e o envio perde o evento.");
    }

    @Override
    public void publishPaymentRequested(PaymentRequestedEvent event) {
        publishAfterCommit(PaymentTopology.PAYMENT_REQUESTED, event.eventId(), event.orderId(), event);
    }

    @Override
    public void publishRefundRequested(RefundRequestedEvent event) {
        publishAfterCommit(PaymentTopology.REFUND_REQUESTED, event.eventId(), event.orderId(), event);
    }

    private void publishAfterCommit(String routingKey, UUID eventId, UUID orderId, Object event) {
        // O MDC é lido agora: o afterCommit roda na mesma thread, mas o valor fica explícito.
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        AfterCommit.run(() -> send(routingKey, eventId, orderId, correlationId, event));
    }

    private void send(String routingKey, UUID eventId, UUID orderId, String correlationId, Object event) {
        try {
            rabbitTemplate.convertAndSend(PaymentTopology.EXCHANGE, routingKey, event, message -> {
                MessageProperties properties = message.getMessageProperties();
                properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                properties.setMessageId(eventId.toString());
                properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                if (correlationId != null) {
                    properties.setHeader(CorrelationIdFilter.HEADER_NAME, correlationId);
                }
                return message;
            }, new CorrelationData(eventId.toString()));

            log.info("Evento publicado: routingKey={} eventId={} orderId={}", routingKey, eventId, orderId);
        } catch (AmqpException e) {
            log.error("EVENTO PERDIDO (sem Outbox): falha ao publicar routingKey={} eventId={} orderId={}. "
                + "O pedido já foi gravado; reprocesse manualmente.", routingKey, eventId, orderId, e);
        }
    }
}
