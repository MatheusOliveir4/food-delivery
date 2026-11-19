package com.food_delivery.paymentservice.exceptions;

/** Operação não permitida no status atual do pagamento (por exemplo, estornar duas vezes). */
public class InvalidPaymentStateException extends RuntimeException {
    public InvalidPaymentStateException(String message) {
        super(message);
    }
}
