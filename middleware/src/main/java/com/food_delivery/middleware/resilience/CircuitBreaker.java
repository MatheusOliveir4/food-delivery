package com.food_delivery.middleware.resilience;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CircuitBreaker {
    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

    public enum State {
        CLOSED(0),
        HALF_OPEN(1),
        OPEN(2);

        private final int code;

        State(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }
    }

    private final String name;
    private final int failureThreshold;
    private final Duration openDuration;
    private final Clock clock;

    private State state = State.CLOSED;
    private int consecutiveFailures;
    private Instant openedAt;
    private boolean trialInFlight;

    public CircuitBreaker(String name, int failureThreshold, Duration openDuration, Clock clock) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
        this.clock = clock;
    }

    public synchronized boolean tryAcquire() {
        if (state == State.OPEN) {
            if (Duration.between(openedAt, clock.instant()).compareTo(openDuration) < 0) {
                return false;
            }
            transitionTo(State.HALF_OPEN);
        }

        if (state == State.HALF_OPEN) {
            if (trialInFlight) {
                return false;
            }
            trialInFlight = true;
        }

        return true;
    }

    public synchronized void recordSuccess() {
        trialInFlight = false;
        consecutiveFailures = 0;
        if (state != State.CLOSED) {
            transitionTo(State.CLOSED);
        }
    }

    public synchronized void recordFailure() {
        trialInFlight = false;

        if (state == State.HALF_OPEN) {
            open();
            return;
        }

        consecutiveFailures++;
        if (state == State.CLOSED && consecutiveFailures >= failureThreshold) {
            open();
        }
    }

    public synchronized State state() {
        return state;
    }

    public String name() {
        return name;
    }

    private void open() {
        openedAt = clock.instant();
        transitionTo(State.OPEN);
    }

    private void transitionTo(State next) {
        log.warn("Circuit breaker do serviço {}: {} -> {}", name, state, next);
        state = next;
        if (next == State.CLOSED) {
            consecutiveFailures = 0;
        }
    }
}
