package com.food_delivery.orderservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.food_delivery.orderservice.clients.stub.StubData;
import com.jayway.jsonpath.JsonPath;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("stub")
class OrderFlowTests {
    @Autowired
    private MockMvc mvc;

    @Value("${app.jwt.secret}")
    private String secret;

    private String token(long userId, String role) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("role", role)
            .expiration(new Date(System.currentTimeMillis() + 60_000))
            .signWith(key)
            .compact();
    }

    private String item(Object productId, int quantity) {
        return "{\"productId\":\"" + productId + "\",\"quantity\":" + quantity + "}";
    }

    private String addToCart(String auth, Object productId, int quantity) throws Exception {
        return mvc.perform(post("/cart/items").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(item(productId, quantity)))
            .andReturn().getResponse().getContentAsString();
    }

    private String checkout(String auth, String method) throws Exception {
        String body = mvc.perform(post("/orders/checkout").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"" + method + "\"}"))
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void requestWithoutTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/cart/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotReadAnotherCustomersCart() throws Exception {
        mvc.perform(get("/cart/2").header("Authorization", token(1, "CUSTOMER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void fullFlowFromCartToReady() throws Exception {
        String customer = token(10, "CUSTOMER");
        String owner = token(99, "RESTAURANT_OWNER");

        mvc.perform(get("/cart/10").header("Authorization", customer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(0));

        mvc.perform(post("/cart/items").header("Authorization", customer)
                .contentType(MediaType.APPLICATION_JSON).content(item(StubData.BURGER_ID, 2)))
            .andExpect(status().isCreated());
        mvc.perform(post("/cart/items").header("Authorization", customer)
                .contentType(MediaType.APPLICATION_JSON).content(item(StubData.FRIES_ID, 1)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.subtotal").value(64.30));

        mvc.perform(post("/cart/items").header("Authorization", customer)
                .contentType(MediaType.APPLICATION_JSON).content(item(StubData.SOLD_OUT_ID, 1)))
            .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/cart/items").header("Authorization", customer)
                .contentType(MediaType.APPLICATION_JSON).content(item(StubData.OTHER_RESTAURANT_PRODUCT_ID, 1)))
            .andExpect(status().isUnprocessableEntity());

        String body = mvc.perform(post("/orders/checkout").header("Authorization", customer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"PIX\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
            .andExpect(jsonPath("$.total").value(64.30))
            .andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(body, "$.id");

        mvc.perform(get("/cart/10").header("Authorization", customer))
            .andExpect(jsonPath("$.items.length()").value(0));

        mvc.perform(patch("/orders/" + orderId + "/accept").header("Authorization", owner))
            .andExpect(status().isConflict());

        mvc.perform(post("/dev/payments/" + orderId + "/approve").header("Authorization", customer))
            .andExpect(status().isNoContent());

        mvc.perform(get("/orders/restaurant/" + StubData.RESTAURANT_OPEN_ID + "?status=PENDING_RESTAURANT")
                .header("Authorization", owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(orderId));

        mvc.perform(patch("/orders/" + orderId + "/accept").header("Authorization", owner))
            .andExpect(jsonPath("$.status").value("ACCEPTED"));
        mvc.perform(patch("/orders/" + orderId + "/preparation").header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PREPARING\"}"))
            .andExpect(jsonPath("$.status").value("PREPARING"));
        mvc.perform(patch("/orders/" + orderId + "/preparation").header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"READY\"}"))
            .andExpect(jsonPath("$.status").value("READY"));

        mvc.perform(get("/orders/customer/10").header("Authorization", customer))
            .andExpect(jsonPath("$[0].items.length()").value(2));

        mvc.perform(patch("/orders/" + orderId + "/accept").header("Authorization", customer))
            .andExpect(status().isForbidden());
    }

    @Test
    void rejectedPaymentAndRestaurantRejection() throws Exception {
        String customer = token(20, "CUSTOMER");
        String owner = token(99, "RESTAURANT_OWNER");

        addToCart(customer, StubData.BURGER_ID, 1);
        String orderId = checkout(customer, "CREDIT_CARD");

        mvc.perform(post("/dev/payments/" + orderId + "/reject").header("Authorization", customer))
            .andExpect(status().isNoContent());
        mvc.perform(get("/orders/" + orderId).header("Authorization", customer))
            .andExpect(jsonPath("$.status").value("PAYMENT_REJECTED"));

        addToCart(customer, StubData.FRIES_ID, 1);
        orderId = checkout(customer, "PIX");

        mvc.perform(post("/dev/payments/" + orderId + "/approve").header("Authorization", customer));
        mvc.perform(patch("/orders/" + orderId + "/reject").header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Sem ingredientes\"}"))
            .andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.rejectionReason").value("Sem ingredientes"));

        mvc.perform(post("/orders/checkout").header("Authorization", customer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"PIX\"}"))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void ownerOfAnotherRestaurantCannotManageOrders() throws Exception {
        String customer = token(40, "CUSTOMER");
        String otherOwner = token(77, "RESTAURANT_OWNER");
        String admin = token(1, "ADMIN");

        addToCart(customer, StubData.BURGER_ID, 1);
        String orderId = checkout(customer, "PIX");
        mvc.perform(post("/dev/payments/" + orderId + "/approve").header("Authorization", customer));

        mvc.perform(get("/orders/" + orderId).header("Authorization", otherOwner))
            .andExpect(status().isForbidden());
        mvc.perform(get("/orders/restaurant/" + StubData.RESTAURANT_OPEN_ID).header("Authorization", otherOwner))
            .andExpect(status().isForbidden());
        mvc.perform(patch("/orders/" + orderId + "/accept").header("Authorization", otherOwner))
            .andExpect(status().isForbidden());

        mvc.perform(patch("/orders/" + orderId + "/accept").header("Authorization", admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void cartItemCanBeUpdatedAndRemoved() throws Exception {
        String customer = token(30, "CUSTOMER");

        String itemId = JsonPath.read(addToCart(customer, StubData.BURGER_ID, 1), "$.items[0].id");

        mvc.perform(put("/cart/items/" + itemId).header("Authorization", customer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":3}"))
            .andExpect(jsonPath("$.subtotal").value(77.70));

        mvc.perform(delete("/cart/items/" + itemId).header("Authorization", customer))
            .andExpect(status().isNoContent());
        mvc.perform(get("/cart/30").header("Authorization", customer))
            .andExpect(jsonPath("$.items.length()").value(0));

        mvc.perform(post("/cart/items").header("Authorization", customer)
                .contentType(MediaType.APPLICATION_JSON).content(item(StubData.BURGER_ID, 0)))
            .andExpect(status().isBadRequest());
    }
}
