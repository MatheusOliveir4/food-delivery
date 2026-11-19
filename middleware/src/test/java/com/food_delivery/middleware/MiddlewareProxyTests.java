package com.food_delivery.middleware;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.sun.net.httpserver.HttpServer;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@SpringBootTest(properties = {
    "middleware.read-timeout=500ms",
    "middleware.retry.initial-backoff=10ms",
    "middleware.circuit-breaker.failure-threshold=3",
    "middleware.circuit-breaker.open-duration=30s",
    "logging.structured.format.console="
})
@AutoConfigureMockMvc
class MiddlewareProxyTests {
    private static final String SECRET = "troque-esta-chave-por-uma-bem-grande-e-secreta-123";

    static final FakeService USER = new FakeService();
    static final FakeService RESTAURANT = new FakeService();
    static final FakeService CATALOG = new FakeService();
    static final FakeService PAYMENT = new FakeService();
    static final int CLOSED_PORT = closedPort();

    @Autowired
    private MockMvc mvc;

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        registry.add("middleware.services.user", USER::url);
        registry.add("middleware.services.restaurant", RESTAURANT::url);
        registry.add("middleware.services.catalog", CATALOG::url);
        registry.add("middleware.services.payment", PAYMENT::url);
        registry.add("middleware.services.order", () -> "http://localhost:" + CLOSED_PORT);
    }

    @AfterAll
    static void stop() {
        USER.stop();
        RESTAURANT.stop();
        CATALOG.stop();
        PAYMENT.stop();
    }

    @BeforeEach
    void reset() {
        USER.reset();
        RESTAURANT.reset();
        CATALOG.reset();
        PAYMENT.reset();
    }

    private static String token(long id, String role) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder()
            .subject(String.valueOf(id))
            .claim("role", role)
            .expiration(new Date(System.currentTimeMillis() + 60_000))
            .signWith(key)
            .compact();
    }

    @Test
    void publicGetIsForwardedWithPathQueryAndCorrelationId() throws Exception {
        mvc.perform(get("/products/abc").queryParam("x", "1").header("X-Correlation-ID", "demo-1"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Correlation-ID", "demo-1"))
            .andExpect(content().string("{\"ok\":true}"));

        FakeService.Received received = CATALOG.received.peek();
        assertEquals("GET", received.method());
        assertEquals("/products/abc?x=1", received.uri());
        assertEquals("demo-1", received.correlationId());
    }

    @Test
    void menuGoesToCatalogAndOtherRestaurantRoutesToRestaurant() throws Exception {
        mvc.perform(get("/restaurants/r1/menu")).andExpect(status().isOk());
        assertEquals(1, CATALOG.hits.get());
        assertEquals(0, RESTAURANT.hits.get());

        mvc.perform(get("/restaurants/r1")).andExpect(status().isOk());
        assertEquals(1, RESTAURANT.hits.get());
    }

    @Test
    void writeOnRestaurantRequiresOwnerOrAdmin() throws Exception {
        String body = "{\"name\":\"Burger\"}";

        mvc.perform(post("/restaurants").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Token ausente ou inválido"));

        mvc.perform(post("/restaurants").header("Authorization", token(10, "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());

        assertEquals(0, RESTAURANT.hits.get());

        RESTAURANT.respond(attempt -> 201);
        mvc.perform(post("/restaurants").header("Authorization", token(99, "RESTAURANT_OWNER"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());

        FakeService.Received received = RESTAURANT.received.peek();
        assertEquals(body, received.body());
        assertTrue(received.authorization().startsWith("Bearer "));
    }

    @Test
    void unknownRouteReturns404() throws Exception {
        mvc.perform(get("/nada"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void transientErrorsAreRetriedForGet() throws Exception {
        PAYMENT.respond(attempt -> attempt <= 2 ? 503 : 200);

        mvc.perform(get("/payments/p1").header("Authorization", token(10, "CUSTOMER")))
            .andExpect(status().isOk());

        assertEquals(3, PAYMENT.hits.get());
    }

    @Test
    void postIsNotRetried() throws Exception {
        USER.respond(attempt -> 503);

        mvc.perform(post("/users/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isServiceUnavailable());

        assertEquals(1, USER.hits.get());
    }

    @Test
    void slowServiceTimesOut() throws Exception {
        USER.delayMillis = 2000;
        long start = System.currentTimeMillis();

        mvc.perform(get("/users/1").header("Authorization", token(10, "CUSTOMER")))
            .andExpect(status().isGatewayTimeout());

        assertTrue(System.currentTimeMillis() - start < 1500);
    }

    @Test
    void unavailableServiceOpensTheCircuit() throws Exception {
        String auth = token(10, "CUSTOMER");

        mvc.perform(get("/orders/o1").header("Authorization", auth))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error").value("Serviço order indisponível"));

        mvc.perform(get("/orders/o1").header("Authorization", auth))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error").value("Serviço order indisponível (circuit breaker aberto)"));

        mvc.perform(get("/middleware/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.services.order.circuitBreaker").value("OPEN"))
            .andExpect(jsonPath("$.services.order.status").value("UNREACHABLE"))
            .andExpect(jsonPath("$.services.catalog.status").value("UP"));

        mvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("middleware_circuit_state{application=\"middleware\",service=\"order\"} 2.0")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("middleware_requests_seconds_count")));
    }

    private static int closedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static final class FakeService {
        record Received(String method, String uri, String correlationId, String authorization, String body) {
        }

        final AtomicInteger hits = new AtomicInteger();
        final ConcurrentLinkedQueue<Received> received = new ConcurrentLinkedQueue<>();
        volatile IntFunction<Integer> statusForAttempt = attempt -> 200;
        volatile long delayMillis;
        private final HttpServer server;

        FakeService() {
            try {
                server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            server.createContext("/", exchange -> {
                int attempt = hits.incrementAndGet();
                received.add(new Received(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().toString(),
                    exchange.getRequestHeaders().getFirst("X-Correlation-ID"),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));

                if (exchange.getRequestURI().getPath().equals("/actuator/health")) {
                    hits.decrementAndGet();
                }

                if (delayMillis > 0) {
                    try {
                        Thread.sleep(delayMillis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                try {
                    exchange.sendResponseHeaders(statusForAttempt.apply(attempt), body.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                } catch (IOException ignored) {
                }
            });
            server.start();
        }

        String url() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        void respond(IntFunction<Integer> statuses) {
            statusForAttempt = statuses;
        }

        void reset() {
            hits.set(0);
            received.clear();
            statusForAttempt = attempt -> 200;
            delayMillis = 0;
        }

        void stop() {
            server.stop(0);
        }
    }
}
