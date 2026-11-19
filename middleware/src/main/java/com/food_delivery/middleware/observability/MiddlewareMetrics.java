package com.food_delivery.middleware.observability;

import java.time.Duration;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Component
public class MiddlewareMetrics {
    private final MeterRegistry registry;

    public MiddlewareMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordRequest(String service, String route, String method, int status, Duration duration) {
        Timer.builder("middleware.requests")
            .description("Requisições recebidas pelo middleware")
            .tag("service", service)
            .tag("route", route)
            .tag("method", method)
            .tag("status", String.valueOf(status))
            .tag("outcome", outcome(status))
            .publishPercentileHistogram()
            .register(registry)
            .record(duration);
    }

    public void recordDownstream(String service, String result, Duration duration) {
        Timer.builder("middleware.downstream.requests")
            .description("Chamadas do middleware aos serviços")
            .tag("service", service)
            .tag("result", result)
            .publishPercentileHistogram()
            .register(registry)
            .record(duration);
    }

    public void recordRetry(String service) {
        Counter.builder("middleware.retries")
            .description("Novas tentativas feitas pelo middleware")
            .tag("service", service)
            .register(registry)
            .increment();
    }

    public void recordCircuitRejection(String service) {
        Counter.builder("middleware.circuit.rejections")
            .description("Requisições recusadas na hora porque o circuit breaker estava aberto")
            .tag("service", service)
            .register(registry)
            .increment();
    }

    private static String outcome(int status) {
        if (status >= 500) {
            return "SERVER_ERROR";
        }
        if (status >= 400) {
            return "CLIENT_ERROR";
        }
        return "SUCCESS";
    }
}
