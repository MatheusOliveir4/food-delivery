package com.fooddelivery.payment;

import java.math.BigDecimal;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentConsumer {
    private static final Logger log = LoggerFactory.getLogger(PaymentConsumer.class);

    @RabbitListener(queues = "payment.requested")
    public void processPayment(Map<String, Object> event) {
        String eventId = String.valueOf(event.getOrDefault("eventId", "unknown"));
        String correlationId = String.valueOf(event.getOrDefault("correlationId", ""));
        Object orderId = event.getOrDefault("orderId", "not-provided");
        Object amountValue = event.get("amount");

        if (amountValue == null) {
            log.error("payment_rejected event_id={} reason=missing_amount", eventId);
            throw new IllegalArgumentException("Campo 'amount' é obrigatório");
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(String.valueOf(amountValue));
        } catch (NumberFormatException ex) {
            log.error("payment_rejected event_id={} reason=invalid_amount", eventId);
            throw new IllegalArgumentException("Campo 'amount' deve ser numérico", ex);
        }

        if (amount.signum() <= 0) {
            log.error("payment_rejected event_id={} reason=amount_must_be_positive", eventId);
            throw new IllegalArgumentException("Campo 'amount' deve ser maior que zero");
        }

        // Integração acadêmica: não há cobrança real. O processamento é simulado.
        log.info("payment_received event_id={} correlation_id={} order_id={} amount={}",
                eventId, correlationId, orderId, amount);
        log.info("payment_processed event_id={} status=APPROVED mode=SIMULATED", eventId);
    }
}
