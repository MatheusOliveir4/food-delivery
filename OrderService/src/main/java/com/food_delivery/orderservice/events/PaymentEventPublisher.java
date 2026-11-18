package com.food_delivery.orderservice.events;

public interface PaymentEventPublisher {
    void publishPaymentRequested(PaymentRequestedEvent event);

    void publishRefundRequested(RefundRequestedEvent event);
}
