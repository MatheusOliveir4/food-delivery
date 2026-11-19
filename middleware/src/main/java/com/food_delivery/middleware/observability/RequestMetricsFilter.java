package com.food_delivery.middleware.observability;

import java.io.IOException;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestMetricsFilter extends OncePerRequestFilter {
    public static final String SERVICE_ATTRIBUTE = "middleware.service";
    public static final String ROUTE_ATTRIBUTE = "middleware.route";

    private static final Logger log = LoggerFactory.getLogger(RequestMetricsFilter.class);

    private final MiddlewareMetrics metrics;

    public RequestMetricsFilter(MiddlewareMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.startsWith("/middleware");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        long start = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            Duration duration = Duration.ofNanos(System.nanoTime() - start);
            String service = attribute(request, SERVICE_ATTRIBUTE, "none");
            String route = attribute(request, ROUTE_ATTRIBUTE, "unmatched");
            int status = response.getStatus();

            metrics.recordRequest(service, route, request.getMethod(), status, duration);
            log.info("{} {} -> service={} status={} duration_ms={}",
                request.getMethod(), request.getRequestURI(), service, status, duration.toMillis());
        }
    }

    private static String attribute(HttpServletRequest request, String name, String fallback) {
        Object value = request.getAttribute(name);
        return value == null ? fallback : value.toString();
    }
}
