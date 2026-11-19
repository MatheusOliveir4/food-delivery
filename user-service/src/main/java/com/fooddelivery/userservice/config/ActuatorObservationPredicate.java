package com.fooddelivery.userservice.config;

import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;

@Component
public class ActuatorObservationPredicate implements ObservationPredicate {

    @Override
    public boolean test(String name, Observation.Context context) {
        if (context instanceof ServerRequestObservationContext serverContext) {
            return !serverContext.getCarrier().getRequestURI().startsWith("/actuator");
        }
        return true;
    }
}
