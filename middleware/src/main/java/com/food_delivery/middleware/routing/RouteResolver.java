package com.food_delivery.middleware.routing;

import java.util.List;
import java.util.Optional;

import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import com.food_delivery.middleware.config.MiddlewareProperties;

@Component
public class RouteResolver {
    private final List<CompiledRoute> routes;

    public RouteResolver(MiddlewareProperties properties) {
        this.routes = properties.routes().stream()
            .map(route -> {
                properties.serviceUrl(route.service());
                return new CompiledRoute(route, PathPatternParser.defaultInstance.parse(route.path()));
            })
            .toList();
    }

    public Optional<RouteDefinition> resolve(String method, String path) {
        PathContainer container = PathContainer.parsePath(path);
        return routes.stream()
            .filter(route -> route.definition().acceptsMethod(method) && route.pattern().matches(container))
            .map(CompiledRoute::definition)
            .findFirst();
    }

    public List<RouteDefinition> all() {
        return routes.stream().map(CompiledRoute::definition).toList();
    }

    private record CompiledRoute(RouteDefinition definition, PathPattern pattern) {
    }
}
