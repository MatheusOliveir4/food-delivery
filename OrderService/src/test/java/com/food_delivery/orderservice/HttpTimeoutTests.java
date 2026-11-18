package com.food_delivery.orderservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.food_delivery.orderservice.clients.CatalogClient;
import com.food_delivery.orderservice.clients.dto.ProductInfo;
import com.food_delivery.orderservice.exceptions.ExternalServiceException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

@SpringBootTest(properties = {
    "http.client.connect-timeout=2s",
    "http.client.read-timeout=1s"
})
class HttpTimeoutTests {
    private static final UUID FAST_ID = UUID.fromString("00000000-0000-0000-0000-00000000000f");
    private static final UUID SLOW_ID = UUID.fromString("00000000-0000-0000-0000-00000000000e");

    private static final HttpServer FAKE_CATALOG = startFakeCatalog();

    @Autowired
    private CatalogClient catalogClient;

    @DynamicPropertySource
    static void catalogUrl(DynamicPropertyRegistry registry) {
        registry.add("app.services.catalog-url",
            () -> "http://localhost:" + FAKE_CATALOG.getAddress().getPort());
    }

    @AfterAll
    static void stopFakeCatalog() {
        FAKE_CATALOG.stop(0);
    }

    @Test
    void fastResponseIsReturned() {
        ProductInfo product = catalogClient.findProduct(FAST_ID).orElseThrow();

        assertEquals("Produto rápido", product.name());
    }

    @Test
    void slowResponseFailsByReadTimeout() {
        long start = System.nanoTime();

        assertThrows(ExternalServiceException.class, () -> catalogClient.findProduct(SLOW_ID));

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMs >= 900 && elapsedMs < 2500,
            "Esperava falhar perto de 1s (read-timeout), mas levou " + elapsedMs + " ms");
    }

    private static HttpServer startFakeCatalog() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/products/", HttpTimeoutTests::respond);
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void respond(HttpExchange exchange) throws IOException {
        String id = exchange.getRequestURI().getPath().replace("/products/", "");

        if (SLOW_ID.toString().equals(id)) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        byte[] body = ("{\"id\":\"" + id + "\",\"name\":\"Produto rápido\",\"price\":10.00,"
            + "\"available\":true,\"restaurantId\":\"" + UUID.randomUUID() + "\"}")
            .getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().add("Content-Type", "application/json");
        try {
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        } catch (IOException ignored) {
        }
    }
}
