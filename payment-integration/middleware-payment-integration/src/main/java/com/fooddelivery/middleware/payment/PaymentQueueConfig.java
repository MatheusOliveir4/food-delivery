package com.fooddelivery.middleware.payment;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentQueueConfig {
    public static final String PAYMENT_QUEUE = "payment.requested";

    @Bean
    public Queue paymentRequestedQueue() {
        return QueueBuilder.durable(PAYMENT_QUEUE).build();
    }
}
