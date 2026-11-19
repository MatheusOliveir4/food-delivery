package com.food_delivery.orderservice.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.food_delivery.orderservice.events.PaymentApprovedEvent;
import com.food_delivery.orderservice.events.PaymentRefundedEvent;
import com.food_delivery.orderservice.events.PaymentRejectedEvent;
import com.food_delivery.orderservice.services.OrderService;

/**
 * Consome {@code order.payment-result.queue}. A fila recebe três tipos de evento, então o tipo
 * é escolhido pela routing key (parte do contrato) em vez do header __TypeId__.
 *
 * <ul>
 *   <li>Pedido inexistente ({@code ResourceNotFoundException}) ou payload inválido: DLQ sem retry.</li>
 *   <li>Evento repetido: {@link OrderService} ignora pedidos fora de PENDING_PAYMENT.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentResultListener {
    private static final Logger log = LoggerFactory.getLogger(PaymentResultListener.class);

    private final OrderService orderService;
    private final EventReader eventReader;

    public PaymentResultListener(OrderService orderService, EventReader eventReader) {
        this.orderService = orderService;
        this.eventReader = eventReader;
    }

    @RabbitListener(queues = PaymentTopology.ORDER_PAYMENT_RESULT_QUEUE)
    public void onPaymentResult(Message message) {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();

        switch (routingKey == null ? "" : routingKey) {
            case PaymentTopology.PAYMENT_APPROVED -> {
                PaymentApprovedEvent event = eventReader.read(message, PaymentApprovedEvent.class);
                log.info("PaymentApproved recebido: orderId={} paymentId={} eventId={}",
                    event.orderId(), event.paymentId(), event.eventId());
                orderService.handlePaymentApproved(event.orderId());
            }
            case PaymentTopology.PAYMENT_REJECTED -> {
                PaymentRejectedEvent event = eventReader.read(message, PaymentRejectedEvent.class);
                log.info("PaymentRejected recebido: orderId={} paymentId={} eventId={} motivo={}",
                    event.orderId(), event.paymentId(), event.eventId(), event.reason());
                orderService.handlePaymentRejected(event.orderId(), event.reason());
            }
            case PaymentTopology.PAYMENT_REFUNDED -> {
                PaymentRefundedEvent event = eventReader.read(message, PaymentRefundedEvent.class);
                // OrderStatus não tem REFUNDED: o pedido continua REJECTED e o estorno fica só no Payment.
                log.info("PaymentRefunded recebido: orderId={} paymentId={} valor={} eventId={} (status do pedido não muda)",
                    event.orderId(), event.paymentId(), event.amount(), event.eventId());
            }
            default -> throw new InvalidMessageException(
                "Routing key inesperada em " + PaymentTopology.ORDER_PAYMENT_RESULT_QUEUE + ": " + routingKey);
        }
    }
}
