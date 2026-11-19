package com.food_delivery.orderservice.messaging;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.handler.invocation.MethodArgumentResolutionException;

import com.food_delivery.orderservice.exceptions.ResourceNotFoundException;

/**
 * Erros que não adianta tentar de novo: a mensagem vai direto para a DLQ, sem retry.
 * Qualquer outro erro (banco fora do ar, por exemplo) passa pelo retry com backoff.
 */
public final class NonRetryableErrors {
    private NonRetryableErrors() {
    }

    public static boolean isRetryable(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof AmqpRejectAndDontRequeueException
                    || t instanceof MessageConversionException
                    || t instanceof MethodArgumentNotValidException
                    || t instanceof MethodArgumentResolutionException
                    || t instanceof ResourceNotFoundException) {
                return false;
            }
        }
        return true;
    }
}
