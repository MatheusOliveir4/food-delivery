package com.food_delivery.orderservice.entities.enums;

import java.util.Set;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAYMENT_REJECTED,

    PENDING_RESTAURANT,
    ACCEPTED,
    REJECTED,
    PREPARING,
    READY;

    public Set<OrderStatus> allowedNext() {
        return switch (this) {
            case PENDING_PAYMENT -> Set.of(PENDING_RESTAURANT, PAYMENT_REJECTED);
            case PENDING_RESTAURANT -> Set.of(ACCEPTED, REJECTED);
            case ACCEPTED -> Set.of(PREPARING);
            case PREPARING -> Set.of(READY);
            case PAYMENT_REJECTED, REJECTED, READY -> Set.of();
        };
    }

    public boolean canTransitionTo(OrderStatus target) {
        return allowedNext().contains(target);
    }
}
