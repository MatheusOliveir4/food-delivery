package com.food_delivery.orderservice.config;

import org.slf4j.MDC;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CorrelationIdClientConfig {

    @Bean
    public RestClientCustomizer correlationIdCustomizer() {

        return builder -> builder.requestInterceptor(
                (request, body, execution) -> {

                    String correlationId =
                            MDC.get(CorrelationIdFilter.MDC_KEY);

                    if (correlationId != null &&
                            !correlationId.isBlank()) {

                        request.getHeaders().set(
                                CorrelationIdFilter.HEADER_NAME,
                                correlationId
                        );
                    }

                    return execution.execute(request, body);
                }
        );
    }
}
