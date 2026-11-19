package com.food_delivery.restaurantservice.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.food_delivery.restaurantservice.exceptions.UnauthorizedException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class AuthenticatedUserResolver {
    private final SecretKey key;

    public AuthenticatedUserResolver(@Value("${app.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public AuthenticatedUser resolve(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new UnauthorizedException("Token ausente ou inválido");
        }

        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(authorization.substring(7))
                .getPayload();

            return new AuthenticatedUser(Long.valueOf(claims.getSubject()), claims.get("role", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            throw new UnauthorizedException("Token ausente ou inválido");
        }
    }
}
