package com.food_delivery.paymentservice.messaging;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;

import com.food_delivery.paymentservice.config.CorrelationIdFilter;

/**
 * Coloca o header X-Correlation-ID da mensagem no MDC durante o processamento
 * (incluindo as novas tentativas e o envio para a DLQ) e restaura o valor anterior no final.
 */
public class CorrelationIdListenerAdvice implements MethodInterceptor {
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        String correlationId = extract(invocation.getArguments());
        String previous = MDC.get(CorrelationIdFilter.MDC_KEY);

        try {
            if (correlationId != null) {
                MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
            }
            return invocation.proceed();
        } finally {
            if (previous == null) {
                MDC.remove(CorrelationIdFilter.MDC_KEY);
            } else {
                MDC.put(CorrelationIdFilter.MDC_KEY, previous);
            }
        }
    }

    private String extract(Object[] arguments) {
        for (Object argument : arguments) {
            if (argument instanceof Message message) {
                Object value = message.getMessageProperties().getHeader(CorrelationIdFilter.HEADER_NAME);
                return value != null && !value.toString().isBlank() ? value.toString() : null;
            }
        }
        return null;
    }
}
