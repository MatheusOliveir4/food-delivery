package com.food_delivery.middleware.proxy;

import java.io.IOException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.food_delivery.middleware.config.MiddlewareProperties;
import com.food_delivery.middleware.observability.MiddlewareMetrics;
import com.food_delivery.middleware.resilience.CircuitBreaker;
import com.food_delivery.middleware.resilience.CircuitBreakerRegistry;
import com.food_delivery.middleware.routing.RouteDefinition;
import com.food_delivery.middleware.web.MiddlewareException;

@Service
public class ProxyService {
    private static final Logger log = LoggerFactory.getLogger(ProxyService.class);
    private static final Set<Integer> TRANSIENT_STATUSES = Set.of(502, 503, 504);
    private static final Set<String> RETRYABLE_METHODS = Set.of("GET", "HEAD");

    private final DownstreamClient client;
    private final CircuitBreakerRegistry breakers;
    private final MiddlewareMetrics metrics;
    private final MiddlewareProperties properties;

    public ProxyService(DownstreamClient client, CircuitBreakerRegistry breakers,
                        MiddlewareMetrics metrics, MiddlewareProperties properties) {
        this.client = client;
        this.breakers = breakers;
        this.metrics = metrics;
        this.properties = properties;
    }

    public HttpResponse<byte[]> forward(RouteDefinition route, DownstreamRequest request) {
        String service = route.service();
        String baseUrl = properties.serviceUrl(service);
        CircuitBreaker breaker = breakers.get(service);
        Duration readTimeout = properties.readTimeoutFor(service);

        int maxAttempts = RETRYABLE_METHODS.contains(request.method())
            ? Math.max(1, properties.retry().maxAttempts())
            : 1;

        MiddlewareException lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (attempt > 1) {
                metrics.recordRetry(service);
                pause(properties.retry().backoffBefore(attempt));
            }

            if (!breaker.tryAcquire()) {
                metrics.recordCircuitRejection(service);
                throw new MiddlewareException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Serviço " + service + " indisponível (circuit breaker aberto)");
            }

            long start = System.nanoTime();
            try {
                HttpResponse<byte[]> response = client.send(baseUrl, request, readTimeout);
                int status = response.statusCode();
                metrics.recordDownstream(service, String.valueOf(status), elapsed(start));

                if (status >= 500) {
                    breaker.recordFailure();
                } else {
                    breaker.recordSuccess();
                }

                if (TRANSIENT_STATUSES.contains(status) && attempt < maxAttempts) {
                    log.warn("Serviço {} respondeu {} (tentativa {}/{})", service, status, attempt, maxAttempts);
                    continue;
                }
                return response;
            } catch (HttpConnectTimeoutException e) {
                breaker.recordFailure();
                metrics.recordDownstream(service, "UNAVAILABLE", elapsed(start));
                log.warn("Serviço {} não aceitou a conexão a tempo (tentativa {}/{})", service, attempt, maxAttempts);
                lastError = new MiddlewareException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Serviço " + service + " indisponível");
            } catch (HttpTimeoutException e) {
                breaker.recordFailure();
                metrics.recordDownstream(service, "TIMEOUT", elapsed(start));
                log.warn("Serviço {} não respondeu em {} ms", service, readTimeout.toMillis());
                throw new MiddlewareException(HttpStatus.GATEWAY_TIMEOUT,
                    "Serviço " + service + " não respondeu em " + readTimeout.toMillis() + " ms");
            } catch (IOException e) {
                breaker.recordFailure();
                metrics.recordDownstream(service, "UNAVAILABLE", elapsed(start));
                log.warn("Falha ao chamar o serviço {} (tentativa {}/{}): {}",
                    service, attempt, maxAttempts, e.toString());
                lastError = new MiddlewareException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Serviço " + service + " indisponível");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new MiddlewareException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Chamada ao serviço " + service + " interrompida");
            }
        }

        throw lastError;
    }

    private static Duration elapsed(long start) {
        return Duration.ofNanos(System.nanoTime() - start);
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
