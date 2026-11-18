package com.food_delivery.orderservice.clients.rest;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.food_delivery.orderservice.clients.CatalogClient;
import com.food_delivery.orderservice.clients.dto.ProductInfo;
import com.food_delivery.orderservice.exceptions.ExternalServiceException;

@Component
@Profile("!stub")
public class RestCatalogClient implements CatalogClient {
    private final RestClient restClient;

    public RestCatalogClient(
        @Value("${app.services.catalog-url}") String baseUrl,
        @Value("${app.services.timeout-ms}") long timeoutMs
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(factory)
            .build();
    }

    @Override
    public Optional<ProductInfo> findProduct(UUID productId) {
        try {
            return Optional.ofNullable(
                restClient.get()
                    .uri("/products/{id}", productId)
                    .retrieve()
                    .body(ProductInfo.class)
            );
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new ExternalServiceException("Catalog Service indisponível", e);
        }
    }
}
