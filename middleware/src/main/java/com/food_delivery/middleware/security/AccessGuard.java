package com.food_delivery.middleware.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.food_delivery.middleware.config.MiddlewareProperties;
import com.food_delivery.middleware.routing.Access;
import com.food_delivery.middleware.routing.RouteDefinition;
import com.food_delivery.middleware.web.MiddlewareException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class AccessGuard {
    private final SecretKey key;

    public AccessGuard(MiddlewareProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.jwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    public void check(RouteDefinition route, String authorization) {
        if (route.access() == Access.PUBLIC) {
            return;
        }

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new MiddlewareException(HttpStatus.UNAUTHORIZED, "Token ausente ou inválido");
        }

        Claims claims;
        try {
            claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(authorization.substring(7))
                .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new MiddlewareException(HttpStatus.UNAUTHORIZED, "Token ausente ou inválido");
        }

        String role = claims.get("role", String.class);
        if (!route.roles().isEmpty() && !route.roles().contains(role)) {
            throw new MiddlewareException(HttpStatus.FORBIDDEN, "Acesso negado");
        }
    }
}
