package com.food_delivery.paymentservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import com.food_delivery.paymentservice.entities.Payment;
import com.food_delivery.paymentservice.entities.enums.PaymentStatus;
import com.food_delivery.paymentservice.entities.enums.TransactionType;
import com.food_delivery.paymentservice.messaging.PaymentTopology;
import com.food_delivery.paymentservice.repositories.PaymentRepository;
import com.food_delivery.paymentservice.simulation.PaymentSimulator;
import com.food_delivery.paymentservice.simulation.PaymentSimulator.Decision;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import tools.jackson.databind.json.JsonMapper;

/**
 * Payment + RabbitMQ + PostgreSQL reais (Testcontainers, com as migrações do Flyway).
 * O teste faz o papel do Order Service usando só o contrato JSON. É pulado quando não há Docker.
 */
@SpringBootTest(properties = "app.messaging.enabled=true")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
class PaymentMessagingTests {
    private static final String CORRELATION_HEADER = "X-Correlation-ID";
    private static final String FAKE_ORDER_QUEUE = "test.fake-order-service.queue";
    private static final String REQUESTED_DLQ = PaymentTopology.PAYMENT_REQUESTED_QUEUE + PaymentTopology.DLQ_SUFFIX;
    private static final String REFUND_DLQ = PaymentTopology.REFUND_REQUESTED_QUEUE + PaymentTopology.DLQ_SUFFIX;

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3-management"));

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

    @MockitoBean
    private PaymentSimulator simulator;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Value("${app.jwt.secret}")
    private String secret;

    @BeforeEach
    void fakeOrderServiceQueue() {
        // Emula a order.payment-result.queue: recebe tudo o que o Payment publica.
        Queue queue = new Queue(FAKE_ORDER_QUEUE, false, false, false);
        TopicExchange exchange = new TopicExchange(PaymentTopology.EXCHANGE);
        amqpAdmin.declareQueue(queue);
        for (String key : new String[] {
                PaymentTopology.PAYMENT_APPROVED, PaymentTopology.PAYMENT_REJECTED, PaymentTopology.PAYMENT_REFUNDED}) {
            amqpAdmin.declareBinding(BindingBuilder.bind(queue).to(exchange).with(key));
        }
        amqpAdmin.purgeQueue(FAKE_ORDER_QUEUE, false);
        amqpAdmin.purgeQueue(REQUESTED_DLQ, false);
        amqpAdmin.purgeQueue(REFUND_DLQ, false);

        when(simulator.decide(any(), any())).thenReturn(Decision.approve());
    }

    @Test
    void approvedPaymentIsStoredAndPaymentApprovedIsPublished(CapturedOutput output) {
        UUID orderId = UUID.randomUUID();
        send(PaymentTopology.PAYMENT_REQUESTED, "corr-pay-1", paymentRequested(orderId, 501L, "49.90"));

        Message result = receiveResult();
        assertThat(result.getMessageProperties().getReceivedRoutingKey()).isEqualTo(PaymentTopology.PAYMENT_APPROVED);
        assertThat((String) result.getMessageProperties().getHeader(CORRELATION_HEADER)).isEqualTo("corr-pay-1");
        assertThat(result.getMessageProperties().getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(result.getMessageProperties().getHeaders()).doesNotContainKey("__TypeId__");

        Map<String, Object> body = json(result);
        assertThat(body.get("orderId")).isEqualTo(orderId.toString());
        Instant.parse((String) body.get("occurredAt"));

        Payment payment = loadPayment(orderId);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getAmount()).isEqualByComparingTo("49.90");
        assertThat(payment.getTransactions()).hasSize(1);
        assertThat(body.get("paymentId")).isEqualTo(payment.getId().toString());
        assertThat(body.get("transactionId")).isEqualTo(payment.getTransactions().get(0).getId().toString());
        assertThat(output).contains("[corr-pay-1] ").contains("PaymentRequested recebido");
    }

    @Test
    void rejectedPaymentPublishesPaymentRejected() {
        when(simulator.decide(any(), any())).thenReturn(Decision.reject("Saldo insuficiente"));
        UUID orderId = UUID.randomUUID();
        send(PaymentTopology.PAYMENT_REQUESTED, null, paymentRequested(orderId, 502L, "10.00"));

        Message result = receiveResult();
        assertThat(result.getMessageProperties().getReceivedRoutingKey()).isEqualTo(PaymentTopology.PAYMENT_REJECTED);
        assertThat(json(result).get("reason")).isEqualTo("Saldo insuficiente");
        assertThat(loadPayment(orderId).getStatus()).isEqualTo(PaymentStatus.REJECTED);
    }

    @Test
    void duplicatePaymentRequestedChargesOnceAndResendsTheSameResult() {
        UUID orderId = UUID.randomUUID();
        Map<String, Object> request = paymentRequested(orderId, 503L, "20.00");

        send(PaymentTopology.PAYMENT_REQUESTED, null, request);
        Map<String, Object> first = json(receiveResult());
        send(PaymentTopology.PAYMENT_REQUESTED, null, request);
        Map<String, Object> second = json(receiveResult());

        assertThat(second.get("paymentId")).isEqualTo(first.get("paymentId"));
        assertThat(second.get("transactionId")).isEqualTo(first.get("transactionId"));
        verify(simulator, times(1)).decide(any(), any());

        Payment payment = loadPayment(orderId);
        assertThat(payment.getTransactions()).hasSize(1);
        assertThat(paymentRepository.findAll().stream().filter(p -> p.getOrderId().equals(orderId))).hasSize(1);
    }

    @Test
    void refundRequestedRefundsOnceAndPublishesPaymentRefunded() {
        UUID orderId = UUID.randomUUID();
        send(PaymentTopology.PAYMENT_REQUESTED, null, paymentRequested(orderId, 504L, "30.00"));
        receiveResult();

        send(PaymentTopology.REFUND_REQUESTED, "corr-refund-1", refundRequested(orderId, "Restaurante rejeitou"));
        Message refunded = receiveResult();
        assertThat(refunded.getMessageProperties().getReceivedRoutingKey()).isEqualTo(PaymentTopology.PAYMENT_REFUNDED);
        assertThat((String) refunded.getMessageProperties().getHeader(CORRELATION_HEADER)).isEqualTo("corr-refund-1");
        assertThat(json(refunded).get("amount").toString()).isEqualTo("30.0");

        Payment payment = loadPayment(orderId);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getTransactions()).extracting(t -> t.getType())
            .containsExactly(TransactionType.CHARGE, TransactionType.REFUND);
        assertThat(payment.getTransactions().get(1).getReason()).isEqualTo("Restaurante rejeitou");

        // Segundo pedido de estorno: descartado com log, sem novo evento e sem DLQ.
        send(PaymentTopology.REFUND_REQUESTED, null, refundRequested(orderId, "de novo"));
        awaitQueueEmpty(PaymentTopology.REFUND_REQUESTED_QUEUE);
        assertThat(rabbitTemplate.receive(FAKE_ORDER_QUEUE, 1_000)).isNull();
        assertThat(messageCount(REFUND_DLQ)).isZero();
        assertThat(loadPayment(orderId).getTransactions()).hasSize(2);
    }

    @Test
    void refundForUnknownOrderIsDiscardedWithoutDlq() {
        send(PaymentTopology.REFUND_REQUESTED, null, refundRequested(UUID.randomUUID(), "sem pagamento"));

        awaitQueueEmpty(PaymentTopology.REFUND_REQUESTED_QUEUE);
        assertThat(messageCount(REFUND_DLQ)).isZero();
        assertThat(rabbitTemplate.receive(FAKE_ORDER_QUEUE, 500)).isNull();
    }

    @Test
    void invalidJsonGoesStraightToDlqWithoutRetry() {
        Instant sentAt = Instant.now();
        sendRaw(PaymentTopology.PAYMENT_REQUESTED, "não é json".getBytes(StandardCharsets.UTF_8));

        assertThat(rabbitTemplate.receive(REQUESTED_DLQ, 10_000)).isNotNull();
        assertThat(Duration.between(sentAt, Instant.now())).isLessThan(Duration.ofMillis(1900));
    }

    @Test
    void invalidAmountGoesStraightToDlq() {
        Instant sentAt = Instant.now();
        send(PaymentTopology.PAYMENT_REQUESTED, null, paymentRequested(UUID.randomUUID(), 505L, "-5.00"));

        assertThat(rabbitTemplate.receive(REQUESTED_DLQ, 10_000)).isNotNull();
        assertThat(Duration.between(sentAt, Instant.now())).isLessThan(Duration.ofMillis(1900));
        verify(simulator, times(0)).decide(any(), any());
    }

    @Test
    void unknownPaymentMethodGoesToDlq() {
        Map<String, Object> request = paymentRequested(UUID.randomUUID(), 506L, "5.00");
        request.put("paymentMethod", "BOLETO");
        send(PaymentTopology.PAYMENT_REQUESTED, null, request);

        assertThat(rabbitTemplate.receive(REQUESTED_DLQ, 10_000)).isNotNull();
    }

    @Test
    void restEndpointsAndRefundUseTheSameRules() throws Exception {
        UUID orderId = UUID.randomUUID();
        send(PaymentTopology.PAYMENT_REQUESTED, null, paymentRequested(orderId, 507L, "15.00"));
        String paymentId = (String) json(receiveResult()).get("paymentId");

        mvc.perform(get("/payments/order/" + orderId).header("Authorization", token(507, "CUSTOMER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(paymentId))
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.transactions[0].type").value("CHARGE"));
        mvc.perform(get("/payments/" + paymentId).header("Authorization", token(999, "CUSTOMER")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/payments/" + paymentId))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/payments/" + paymentId + "/refund").header("Authorization", token(507, "CUSTOMER")))
            .andExpect(status().isForbidden());

        mvc.perform(post("/payments/" + paymentId + "/refund").header("Authorization", token(1, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Cliente desistiu\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REFUNDED"))
            .andExpect(jsonPath("$.transactions.length()").value(2));
        assertThat(receiveResult().getMessageProperties().getReceivedRoutingKey())
            .isEqualTo(PaymentTopology.PAYMENT_REFUNDED);

        mvc.perform(post("/payments/" + paymentId + "/refund").header("Authorization", token(1, "ADMIN")))
            .andExpect(status().isConflict());
        mvc.perform(get("/payments/" + UUID.randomUUID()).header("Authorization", token(1, "ADMIN")))
            .andExpect(status().isNotFound());
    }

    // ---- helpers ----

    private Map<String, Object> paymentRequested(UUID orderId, Long customerId, String amount) {
        // Mesmo formato publicado pelo Order (inclui campos que o Payment não usa).
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("orderId", orderId.toString());
        event.put("customerId", customerId);
        event.put("amount", new BigDecimal(amount));
        event.put("paymentMethod", "PIX");
        event.put("requestedAt", LocalDateTime.now().toString());
        event.put("occurredAt", Instant.now().toString());
        return event;
    }

    private Map<String, Object> refundRequested(UUID orderId, String reason) {
        return Map.of(
            "eventId", UUID.randomUUID().toString(),
            "orderId", orderId.toString(),
            "customerId", 1,
            "amount", new BigDecimal("30.00"),
            "reason", reason,
            "requestedAt", LocalDateTime.now().toString(),
            "occurredAt", Instant.now().toString());
    }

    private void send(String routingKey, String correlationId, Map<String, Object> event) {
        Message message = MessageBuilder.withBody(jsonMapper.writeValueAsBytes(event))
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

    private Message receiveResult() {
        Message message = rabbitTemplate.receive(FAKE_ORDER_QUEUE, 10_000);
        assertThat(message).as("evento publicado pelo Payment").isNotNull();
        return message;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> json(Message message) {
        return jsonMapper.readValue(message.getBody(), Map.class);
    }

    private Payment loadPayment(UUID orderId) {
        return transactionTemplate.execute(tx -> {
            Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
            payment.getTransactions().size();
            return payment;
        });
    }

    private void awaitQueueEmpty(String queue) {
        await().atMost(Duration.ofSeconds(15)).until(() -> messageCount(queue) == 0);
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
