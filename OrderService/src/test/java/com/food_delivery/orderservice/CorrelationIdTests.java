package com.food_delivery.orderservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@SpringBootTest
@AutoConfigureMockMvc
class CorrelationIdTests {
    private static final String HEADER = "X-Correlation-ID";
    private static final AtomicReference<String> RECEIVED_BY_CATALOG = new AtomicReference<>();
    private static final HttpServer FAKE_CATALOG = startFakeCatalog();

    @Autowired
    private MockMvc mvc;

    @Value("${app.jwt.secret}")
    private String secret;

    @DynamicPropertySource
    static void catalogUrl(DynamicPropertyRegistry registry) {
        registry.add("app.services.catalog-url",
            () -> "http://localhost:" + FAKE_CATALOG.getAddress().getPort());
    }

    @AfterAll
    static void stopFakeCatalog() {
        FAKE_CATALOG.stop(0);
    }

    @BeforeEach
    void reset() {
        RECEIVED_BY_CATALOG.set(null);
    }

    @Test
    void receivedIdIsReturnedAndForwardedToCatalog() throws Exception {
        mvc.perform(addItem(50).header(HEADER, "pedido-abc-123"))
            .andExpect(status().isCreated())
            .andExpect(header().string(HEADER, "pedido-abc-123"));

        assertEquals("pedido-abc-123", RECEIVED_BY_CATALOG.get());
    }

    @Test
    void missingIdIsGeneratedAndForwardedToCatalog() throws Exception {
        String generated = mvc.perform(addItem(51))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getHeader(HEADER);

        assertNotNull(generated);
        UUID.fromString(generated);
        assertEquals(generated, RECEIVED_BY_CATALOG.get());
    }

    @Test
    void invalidIdIsReplaced() throws Exception {
        String returned = mvc.perform(addItem(52).header(HEADER, "id com espaço <script>"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getHeader(HEADER);

        assertNotEquals("id com espaço <script>", returned);
        UUID.fromString(returned);
        assertEquals(returned, RECEIVED_BY_CATALOG.get());
    }

    @Test
    void errorResponsesAlsoCarryTheId() throws Exception {
        mvc.perform(get("/cart/1").header(HEADER, "sem-token-1"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HEADER, "sem-token-1"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder addItem(long customerId) {
        return post("/cart/items")
            .header("Authorization", token(customerId))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"productId\":\"" + UUID.randomUUID() + "\",\"quantity\":1}");
    }

    private String token(long userId) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("role", "CUSTOMER")
            .expiration(new Date(System.currentTimeMillis() + 60_000))
            .signWith(key)
            .compact();
    }

    private static HttpServer startFakeCatalog() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/products/", CorrelationIdTests::respond);
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void respond(HttpExchange exchange) throws IOException {
        RECEIVED_BY_CATALOG.set(exchange.getRequestHeaders().getFirst(HEADER));

        String id = exchange.getRequestURI().getPath().replace("/products/", "");
        byte[] body = ("{\"id\":\"" + id + "\",\"name\":\"Produto\",\"price\":10.00,"
            + "\"available\":true,\"restaurantId\":\"00000000-0000-0000-0000-0000000000a1\"}")
            .getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
