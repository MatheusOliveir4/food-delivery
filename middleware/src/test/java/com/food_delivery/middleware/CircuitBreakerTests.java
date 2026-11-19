package com.food_delivery.middleware;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.food_delivery.middleware.resilience.CircuitBreaker;
import com.food_delivery.middleware.resilience.CircuitBreaker.State;

class CircuitBreakerTests {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-19T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final CircuitBreaker breaker = new CircuitBreaker("catalog", 3, Duration.ofSeconds(10), clock);

    private void fail(int times) {
        for (int i = 0; i < times; i++) {
            assertTrue(breaker.tryAcquire());
            breaker.recordFailure();
        }
    }

    @Test
    void opensAfterConsecutiveFailures() {
        fail(2);
        assertEquals(State.CLOSED, breaker.state());

        fail(1);
        assertEquals(State.OPEN, breaker.state());
        assertFalse(breaker.tryAcquire());
    }

    @Test
    void successResetsTheFailureCount() {
        fail(2);
        assertTrue(breaker.tryAcquire());
        breaker.recordSuccess();
        fail(2);

        assertEquals(State.CLOSED, breaker.state());
    }

    @Test
    void halfOpenAllowsOneTrialAndClosesOnSuccess() {
        fail(3);
        clock.advance(Duration.ofSeconds(10));

        assertTrue(breaker.tryAcquire());
        assertEquals(State.HALF_OPEN, breaker.state());
        assertFalse(breaker.tryAcquire());

        breaker.recordSuccess();
        assertEquals(State.CLOSED, breaker.state());
        assertTrue(breaker.tryAcquire());
    }

    @Test
    void halfOpenReopensOnFailure() {
        fail(3);
        clock.advance(Duration.ofSeconds(11));

        assertTrue(breaker.tryAcquire());
        breaker.recordFailure();

        assertEquals(State.OPEN, breaker.state());
        assertFalse(breaker.tryAcquire());
    }
}
