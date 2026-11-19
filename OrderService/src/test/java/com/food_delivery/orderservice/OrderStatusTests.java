package com.food_delivery.orderservice;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.food_delivery.orderservice.entities.enums.OrderStatus;

class OrderStatusTests {
    @Test
    void happyPathTransitionsAreAllowed() {
        assertTrue(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.PENDING_RESTAURANT));
        assertTrue(OrderStatus.PENDING_RESTAURANT.canTransitionTo(OrderStatus.ACCEPTED));
        assertTrue(OrderStatus.ACCEPTED.canTransitionTo(OrderStatus.PREPARING));
        assertTrue(OrderStatus.PREPARING.canTransitionTo(OrderStatus.READY));
    }

    @Test
    void invalidTransitionsAreBlocked() {
        assertFalse(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.ACCEPTED));
        assertFalse(OrderStatus.ACCEPTED.canTransitionTo(OrderStatus.READY));
        assertFalse(OrderStatus.REJECTED.canTransitionTo(OrderStatus.ACCEPTED));
        assertFalse(OrderStatus.READY.canTransitionTo(OrderStatus.PREPARING));
    }
}
