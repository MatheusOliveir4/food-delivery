package com.food_delivery.paymentservice.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Usado quando a mensageria está desligada (app.messaging.enabled=false): só escreve no log. */
@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "false")
public class LoggingPaymentEventPublisher implements PaymentEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(LoggingPaymentEventPublisher.class);

    @Override
    public void publishPaymentApproved(PaymentApprovedEvent event) {
        log.info("[evento] PaymentApproved: {}", event);
    }

    @Override
    public void publishPaymentRejected(PaymentRejectedEvent event) {
        log.info("[evento] PaymentRejected: {}", event);
    }

    @Override
    public void publishPaymentRefunded(PaymentRefundedEvent event) {
        log.info("[evento] PaymentRefunded: {}", event);
    }
}
