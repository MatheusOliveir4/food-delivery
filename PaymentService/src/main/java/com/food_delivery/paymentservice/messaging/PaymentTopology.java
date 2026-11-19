package com.food_delivery.paymentservice.messaging;

/**
 * Nomes do broker usados entre Order e Payment. Os mesmos valores existem no Order Service:
 * o contrato é o nome + o JSON, não uma classe compartilhada.
 */
public final class PaymentTopology {
    public static final String EXCHANGE = "payment.exchange";
    public static final String DEAD_LETTER_EXCHANGE = "payment.dlx";
    public static final String DLQ_SUFFIX = ".dlq";

    public static final String PAYMENT_REQUESTED = "payment.requested";
    public static final String PAYMENT_APPROVED = "payment.approved";
    public static final String PAYMENT_REJECTED = "payment.rejected";
    public static final String REFUND_REQUESTED = "payment.refund-requested";
    public static final String PAYMENT_REFUNDED = "payment.refunded";

    /** Filas consumidas pelo Payment. */
    public static final String PAYMENT_REQUESTED_QUEUE = "payment.requested.queue";
    public static final String REFUND_REQUESTED_QUEUE = "payment.refund-requested.queue";

    private PaymentTopology() {
    }
}
