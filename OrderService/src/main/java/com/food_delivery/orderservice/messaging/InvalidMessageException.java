package com.food_delivery.orderservice.messaging;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;

/** Mensagem que nunca vai ser processada (JSON inválido, campo faltando): vai direto para a DLQ. */
public class InvalidMessageException extends AmqpRejectAndDontRequeueException {
    public InvalidMessageException(String message) {
        super(message);
    }

    public InvalidMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
