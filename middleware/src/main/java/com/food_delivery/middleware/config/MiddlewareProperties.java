package com.food_delivery.middleware.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.food_delivery.middleware.routing.RouteDefinition;

@ConfigurationProperties(prefix = "middleware")
public record MiddlewareProperties(
    String jwtSecret,
    Duration connectTimeout,
    Duration readTimeout,
    Map<String, Duration> serviceReadTimeouts,
    Retry retry,
    CircuitBreaker circuitBreaker,
    Map<String, String> services,
    List<RouteDefinition> routes
) {

    public record Retry(int maxAttempts, Duration initialBackoff, double multiplier) {

        public Duration backoffBefore(int nextAttempt) {
            double factor = Math.pow(multiplier, nextAttempt - 2);
            return Duration.ofMillis(Math.round(initialBackoff.toMillis() * factor));
        }
    }

    public record CircuitBreaker(int failureThreshold, Duration openDuration) {
    }

    public Duration readTimeoutFor(String service) {
        if (serviceReadTimeouts != null && serviceReadTimeouts.containsKey(service)) {
            return serviceReadTimeouts.get(service);
        }
        return readTimeout;
    }

    public String serviceUrl(String service) {
        String url = services.get(service);
        if (url == null) {
            throw new IllegalStateException("Serviço sem URL configurada: " + service);
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
