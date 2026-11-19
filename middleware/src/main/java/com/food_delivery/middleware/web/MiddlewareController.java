package com.food_delivery.middleware.web;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.food_delivery.middleware.config.MiddlewareProperties;
import com.food_delivery.middleware.resilience.CircuitBreakerRegistry;
import com.food_delivery.middleware.routing.RouteDefinition;
import com.food_delivery.middleware.routing.RouteResolver;

@RestController
@RequestMapping("/middleware")
public class MiddlewareController {
    private static final Duration HEALTH_TIMEOUT = Duration.ofMillis(1500);

    private final MiddlewareProperties properties;
    private final CircuitBreakerRegistry breakers;
    private final RouteResolver routeResolver;
    private final HttpClient healthClient = HttpClient.newBuilder().connectTimeout(HEALTH_TIMEOUT).build();

    public MiddlewareController(MiddlewareProperties properties, CircuitBreakerRegistry breakers,
                                RouteResolver routeResolver) {
        this.properties = properties;
        this.breakers = breakers;
        this.routeResolver = routeResolver;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, CompletableFuture<String>> checks = new LinkedHashMap<>();
        for (String service : properties.services().keySet()) {
            checks.put(service, check(properties.serviceUrl(service)));
        }

        Map<String, Object> services = new LinkedHashMap<>();
        checks.forEach((service, status) -> {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("url", properties.serviceUrl(service));
            info.put("status", status.join());
            info.put("circuitBreaker", breakers.get(service).state().name());
            services.put(service, info);
        });

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("middleware", "UP");
        body.put("services", services);
        return body;
    }

    @GetMapping("/routes")
    public List<RouteDefinition> routes() {
        return routeResolver.all();
    }

    private CompletableFuture<String> check(String baseUrl) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/actuator/health"))
            .timeout(HEALTH_TIMEOUT)
            .GET()
            .build();

        return healthClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
            .thenApply(response -> response.statusCode() == 200 ? "UP" : "DOWN")
            .exceptionally(error -> "UNREACHABLE");
    }
}
