package com.food_delivery.middleware.routing;

import java.util.List;

public record RouteDefinition(
    String service,
    String path,
    List<String> methods,
    Access access,
    List<String> roles
) {

    public RouteDefinition {
        methods = methods == null ? List.of() : methods.stream().map(String::toUpperCase).toList();
        access = access == null ? Access.AUTHENTICATED : access;
        roles = roles == null ? List.of() : List.copyOf(roles);
    }

    public boolean acceptsMethod(String method) {
        return methods.isEmpty() || methods.contains(method.toUpperCase());
    }
}
