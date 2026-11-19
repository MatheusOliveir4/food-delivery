package com.food_delivery.orderservice.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingPaymentEventPublisher implements PaymentEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(LoggingPaymentEventPublisher.class);

    @Override
    public void publishPaymentRequested(PaymentRequestedEvent event) {
        log.info("[evento] PaymentRequested: {}", event);
    }

    @Override
    public void publishRefundRequested(RefundRequestedEvent event) {
        log.info("[evento] RefundRequested: {}", event);
    }
}
