package com.food_delivery.middleware.resilience;

import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.food_delivery.middleware.config.MiddlewareProperties;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class CircuitBreakerRegistry {
    private final Map<String, CircuitBreaker> breakers = new LinkedHashMap<>();

    public CircuitBreakerRegistry(MiddlewareProperties properties, MeterRegistry meterRegistry) {
        MiddlewareProperties.CircuitBreaker settings = properties.circuitBreaker();

        for (String service : properties.services().keySet()) {
            CircuitBreaker breaker = new CircuitBreaker(
                service, settings.failureThreshold(), settings.openDuration(), Clock.systemUTC());
            breakers.put(service, breaker);

            Gauge.builder("middleware.circuit.state", breaker, b -> b.state().code())
                .description("Estado do circuit breaker: 0 = CLOSED, 1 = HALF_OPEN, 2 = OPEN")
                .tag("service", service)
                .register(meterRegistry);
        }
    }

    public CircuitBreaker get(String service) {
        CircuitBreaker breaker = breakers.get(service);
        if (breaker == null) {
            throw new IllegalStateException("Serviço sem circuit breaker: " + service);
        }
        return breaker;
    }

    public Collection<CircuitBreaker> all() {
        return breakers.values();
    }
}
