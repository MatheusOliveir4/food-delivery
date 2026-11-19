package com.food_delivery.paymentservice.events;

public interface PaymentEventPublisher {
    void publishPaymentApproved(PaymentApprovedEvent event);

    void publishPaymentRejected(PaymentRejectedEvent event);

    void publishPaymentRefunded(PaymentRefundedEvent event);
}
