package com.food_delivery.orderservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import com.food_delivery.orderservice.clients.stub.StubData;
import com.food_delivery.orderservice.messaging.PaymentTopology;
import com.jayway.jsonpath.JsonPath;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import tools.jackson.databind.json.JsonMapper;

/**
 * Order + RabbitMQ real (Testcontainers). O teste faz o papel do Payment Service usando só o
 * contrato JSON: lê PaymentRequested/RefundRequested e responde com os eventos de resultado.
 * É pulado quando não há Docker.
 */
@SpringBootTest(properties = "app.messaging.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("stub")
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
class PaymentMessagingTests {
    private static final String CORRELATION_HEADER = "X-Correlation-ID";
    private static final String FAKE_PAYMENT_QUEUE = "test.fake-payment-service.queue";
    private static final String RESULT_DLQ = PaymentTopology.ORDER_PAYMENT_RESULT_QUEUE + PaymentTopology.DLQ_SUFFIX;

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3-management"));

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private JsonMapper jsonMapper;

    @Value("${app.jwt.secret}")
    private String secret;

    @BeforeEach
    void fakePaymentServiceQueue() {
        // Emula as filas do Payment: recebe tudo o que o Order publica para ele.
        Queue queue = new Queue(FAKE_PAYMENT_QUEUE, false, false, false);
        TopicExchange exchange = new TopicExchange(PaymentTopology.EXCHANGE);
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareBinding(BindingBuilder.bind(queue).to(exchange).with(PaymentTopology.PAYMENT_REQUESTED));
        amqpAdmin.declareBinding(BindingBuilder.bind(queue).to(exchange).with(PaymentTopology.REFUND_REQUESTED));
        amqpAdmin.purgeQueue(FAKE_PAYMENT_QUEUE, false);
        amqpAdmin.purgeQueue(RESULT_DLQ, false);
    }

    @Test
    void checkoutPublishesPaymentRequestedAndApprovalMovesOrderToPendingRestaurant(CapturedOutput output)
            throws Exception {
        String customer = token(301, "CUSTOMER");
        String orderId = checkout(customer, "corr-approved-301");

        Message request = receiveFromFakePayment();
        assertThat(request.getMessageProperties().getReceivedRoutingKey()).isEqualTo(PaymentTopology.PAYMENT_REQUESTED);
        assertThat((String) request.getMessageProperties().getHeader(CORRELATION_HEADER)).isEqualTo("corr-approved-301");
        assertThat(request.getMessageProperties().getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(request.getMessageProperties().getHeaders()).doesNotContainKey("__TypeId__");

        Map<String, Object> body = json(request);
        assertThat(body.get("orderId")).isEqualTo(orderId);
        assertThat(body.get("customerId")).isEqualTo(301);
        assertThat(body.get("paymentMethod")).isEqualTo("PIX");
        assertThat(body.get("amount").toString()).isEqualTo("25.9");
        UUID.fromString((String) body.get("eventId"));
        Instant.parse((String) body.get("occurredAt")); // ISO-8601

        sendResult(PaymentTopology.PAYMENT_APPROVED, "corr-approved-301", Map.of(
            "eventId", UUID.randomUUID().toString(),
            "paymentId", UUID.randomUUID().toString(),
            "orderId", orderId,
            "transactionId", UUID.randomUUID().toString(),
            "occurredAt", Instant.now().toString()));

        awaitStatus(customer, orderId, "PENDING_RESTAURANT");
        assertThat(output).contains("[corr-approved-301] ").contains("PaymentApproved recebido");
    }

    @Test
    void rejectedPaymentMovesOrderToPaymentRejected() throws Exception {
        String customer = token(302, "CUSTOMER");
        String orderId = checkout(customer, null);
        receiveFromFakePayment();

        sendResult(PaymentTopology.PAYMENT_REJECTED, null, Map.of(
            "eventId", UUID.randomUUID().toString(),
            "paymentId", UUID.randomUUID().toString(),
            "orderId", orderId,
            "reason", "Saldo insuficiente",
            "occurredAt", Instant.now().toString()));

        awaitStatus(customer, orderId, "PAYMENT_REJECTED");
        mvc.perform(get("/orders/" + orderId).header("Authorization", customer))
            .andExpect(jsonPath("$.rejectionReason").value("Saldo insuficiente"));
    }

    @Test
    void restaurantRejectionRequestsRefundAndPaymentRefundedIsConsumed() throws Exception {
        String customer = token(303, "CUSTOMER");
        String owner = token(99, "RESTAURANT_OWNER");
        String orderId = checkout(customer, null);
        receiveFromFakePayment();
        approve(orderId);
        awaitStatus(customer, orderId, "PENDING_RESTAURANT");

        mvc.perform(patch("/orders/" + orderId + "/reject").header("Authorization", owner)
                .header(CORRELATION_HEADER, "corr-refund-303")
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Sem entregador\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED"));

        Message refund = receiveFromFakePayment();
        assertThat(refund.getMessageProperties().getReceivedRoutingKey()).isEqualTo(PaymentTopology.REFUND_REQUESTED);
        assertThat((String) refund.getMessageProperties().getHeader(CORRELATION_HEADER)).isEqualTo("corr-refund-303");
        Map<String, Object> body = json(refund);
        assertThat(body.get("orderId")).isEqualTo(orderId);
        assertThat(body.get("reason")).isEqualTo("Sem entregador");
        assertThat(body.get("amount").toString()).isEqualTo("25.9");
        assertThat(body).containsKeys("eventId", "occurredAt", "customerId", "requestedAt");

        sendResult(PaymentTopology.PAYMENT_REFUNDED, "corr-refund-303", Map.of(
            "eventId", UUID.randomUUID().toString(),
            "paymentId", UUID.randomUUID().toString(),
            "orderId", orderId,
            "amount", 25.90,
            "occurredAt", Instant.now().toString()));

        awaitQueueEmpty(PaymentTopology.ORDER_PAYMENT_RESULT_QUEUE);
        assertThat(messageCount(RESULT_DLQ)).isZero();
        // Não existe REFUNDED em OrderStatus: o pedido continua REJECTED.
        awaitStatus(customer, orderId, "REJECTED");
    }

    @Test
    void duplicatePaymentApprovedIsAppliedOnlyOnce() throws Exception {
        String customer = token(304, "CUSTOMER");
        String owner = token(99, "RESTAURANT_OWNER");
        String orderId = checkout(customer, null);
        receiveFromFakePayment();

        Map<String, Object> approved = approvedEvent(orderId);
        sendResult(PaymentTopology.PAYMENT_APPROVED, null, approved);
        awaitStatus(customer, orderId, "PENDING_RESTAURANT");

        mvc.perform(patch("/orders/" + orderId + "/accept").header("Authorization", owner))
            .andExpect(jsonPath("$.status").value("ACCEPTED"));

        // Mesma mensagem de novo: é consumida, ignorada e o pedido não volta para PENDING_RESTAURANT.
        sendResult(PaymentTopology.PAYMENT_APPROVED, null, approved);
        awaitQueueEmpty(PaymentTopology.ORDER_PAYMENT_RESULT_QUEUE);

        mvc.perform(get("/orders/" + orderId).header("Authorization", customer))
            .andExpect(jsonPath("$.status").value("ACCEPTED"));
        assertThat(messageCount(RESULT_DLQ)).isZero();
    }

    @Test
    void invalidJsonGoesStraightToDlqWithoutRetry() {
        Instant sentAt = Instant.now();
        sendRaw(PaymentTopology.PAYMENT_APPROVED, "{isto não é json".getBytes(StandardCharsets.UTF_8));

        Message dead = rabbitTemplate.receive(RESULT_DLQ, 10_000);
        assertThat(dead).isNotNull();
        // O retry começaria em 2s; chegar antes disso mostra que não houve nova tentativa.
        assertThat(Duration.between(sentAt, Instant.now())).isLessThan(Duration.ofMillis(1900));
        assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).isEqualTo("{isto não é json");
    }

    @Test
    void missingRequiredFieldGoesToDlq() {
        sendResult(PaymentTopology.PAYMENT_APPROVED, null, Map.of(
            "eventId", UUID.randomUUID().toString(),
            "paymentId", UUID.randomUUID().toString()));

        assertThat(rabbitTemplate.receive(RESULT_DLQ, 10_000)).isNotNull();
    }

    @Test
    void unknownOrderGoesToDlqWithoutRetry() {
        Instant sentAt = Instant.now();
        sendResult(PaymentTopology.PAYMENT_APPROVED, null, approvedEvent(UUID.randomUUID().toString()));

        Message dead = rabbitTemplate.receive(RESULT_DLQ, 10_000);
        assertThat(dead).isNotNull();
        assertThat(dead.getMessageProperties().getReceivedRoutingKey()).isEqualTo(RESULT_DLQ);
        assertThat(Duration.between(sentAt, Instant.now())).isLessThan(Duration.ofMillis(1900));
    }

    // ---- helpers ----

    private String checkout(String auth, String correlationId) throws Exception {
        mvc.perform(post("/cart/items").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"" + StubData.BURGER_ID + "\",\"quantity\":1}"))
            .andExpect(status().isCreated());

        var request = post("/orders/checkout").header("Authorization", auth)
            .contentType(MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"PIX\"}");
        if (correlationId != null) {
            request.header(CORRELATION_HEADER, correlationId);
        }

        String body = mvc.perform(request)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void approve(String orderId) {
        sendResult(PaymentTopology.PAYMENT_APPROVED, null, approvedEvent(orderId));
    }

    private Map<String, Object> approvedEvent(String orderId) {
        return Map.of(
            "eventId", UUID.randomUUID().toString(),
            "paymentId", UUID.randomUUID().toString(),
            "orderId", orderId,
            "transactionId", UUID.randomUUID().toString(),
            "occurredAt", Instant.now().toString());
    }

    private void sendResult(String routingKey, String correlationId, Map<String, Object> event) {
        byte[] body = jsonMapper.writeValueAsBytes(event);
        Message message = MessageBuilder.withBody(body)
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .build();
        if (correlationId != null) {
            message.getMessageProperties().setHeader(CORRELATION_HEADER, correlationId);
        }
        rabbitTemplate.send(PaymentTopology.EXCHANGE, routingKey, message);
    }

    private void sendRaw(String routingKey, byte[] body) {
        rabbitTemplate.send(PaymentTopology.EXCHANGE, routingKey,
            MessageBuilder.withBody(body).setContentType(MessageProperties.CONTENT_TYPE_JSON).build());
    }

    private Message receiveFromFakePayment() {
        Message message = rabbitTemplate.receive(FAKE_PAYMENT_QUEUE, 10_000);
        assertThat(message).as("evento publicado pelo Order").isNotNull();
        return message;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> json(Message message) {
        return jsonMapper.readValue(message.getBody(), Map.class);
    }

    private void awaitStatus(String auth, String orderId, String expected) {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
            mvc.perform(get("/orders/" + orderId).header("Authorization", auth))
                .andExpect(jsonPath("$.status").value(expected)));
    }

    private void awaitQueueEmpty(String queue) {
        await().atMost(Duration.ofSeconds(15)).until(() -> messageCount(queue) == 0);
        // Dá tempo do listener terminar a mensagem que acabou de tirar da fila.
        await().pollDelay(Duration.ofMillis(500)).until(() -> true);
    }

    private int messageCount(String queue) {
        Properties properties = amqpAdmin.getQueueProperties(queue);
        return properties == null ? 0 : ((Number) properties.get("QUEUE_MESSAGE_COUNT")).intValue();
    }

    private String token(long userId, String role) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("role", role)
            .expiration(new Date(System.currentTimeMillis() + 60_000))
            .signWith(key)
            .compact();
    }
}
