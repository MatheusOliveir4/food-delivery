package com.food_delivery.orderservice.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
public class HttpTimeoutConfig {

    @Bean
    public RestClientCustomizer restClientTimeoutCustomizer(
            @Value("${http.client.connect-timeout:2s}")
            Duration connectTimeout,

            @Value("${http.client.read-timeout:5s}")
            Duration readTimeout) {

        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

            factory.setConnectTimeout(connectTimeout);
            factory.setReadTimeout(readTimeout);

            builder.requestFactory(factory);
        };
    }
}
