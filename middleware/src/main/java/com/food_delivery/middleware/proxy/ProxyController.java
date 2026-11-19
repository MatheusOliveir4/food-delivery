package com.food_delivery.middleware.proxy;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.food_delivery.middleware.observability.RequestMetricsFilter;
import com.food_delivery.middleware.routing.RouteDefinition;
import com.food_delivery.middleware.routing.RouteResolver;
import com.food_delivery.middleware.security.AccessGuard;
import com.food_delivery.middleware.web.MiddlewareException;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class ProxyController {
    private final RouteResolver routeResolver;
    private final AccessGuard accessGuard;
    private final ProxyService proxyService;

    public ProxyController(RouteResolver routeResolver, AccessGuard accessGuard, ProxyService proxyService) {
        this.routeResolver = routeResolver;
        this.accessGuard = accessGuard;
        this.proxyService = proxyService;
    }

    @RequestMapping("/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) throws IOException {
        String method = request.getMethod();
        String path = request.getRequestURI();

        RouteDefinition route = routeResolver.resolve(method, path)
            .orElseThrow(() -> new MiddlewareException(HttpStatus.NOT_FOUND,
                "Rota não encontrada no middleware: " + method + " " + path));

        request.setAttribute(RequestMetricsFilter.SERVICE_ATTRIBUTE, route.service());
        request.setAttribute(RequestMetricsFilter.ROUTE_ATTRIBUTE, route.path());

        accessGuard.check(route, request.getHeader(HttpHeaders.AUTHORIZATION));

        DownstreamRequest downstream = new DownstreamRequest(
            method, path, request.getQueryString(), headers(request), request.getInputStream().readAllBytes());

        HttpResponse<byte[]> response = proxyService.forward(route, downstream);

        HttpHeaders responseHeaders = new HttpHeaders();
        response.headers().map().forEach((name, values) -> {
            if (DownstreamClient.isForwardableResponseHeader(name)) {
                responseHeaders.addAll(name, values);
            }
        });

        return ResponseEntity.status(response.statusCode()).headers(responseHeaders).body(response.body());
    }

    private static Map<String, List<String>> headers(HttpServletRequest request) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (String name : Collections.list(request.getHeaderNames())) {
            headers.put(name, Collections.list(request.getHeaders(name)));
        }
        return headers;
    }
}
