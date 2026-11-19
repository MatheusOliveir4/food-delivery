package com.food_delivery.middleware.web;

import org.springframework.http.HttpStatus;

public class MiddlewareException extends RuntimeException {
    private final HttpStatus status;

    public MiddlewareException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
