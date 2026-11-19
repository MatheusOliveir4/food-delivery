package com.food_delivery.orderservice.messaging;

/**
 * Nomes do broker usados entre Order e Payment. Os mesmos valores existem no Payment Service:
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

    /** Única fila consumida pelo Order: resultados de pagamento e estorno. */
    public static final String ORDER_PAYMENT_RESULT_QUEUE = "order.payment-result.queue";

    private PaymentTopology() {
    }
}
