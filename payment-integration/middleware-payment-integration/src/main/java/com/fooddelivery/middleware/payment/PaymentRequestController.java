package com.fooddelivery.middleware.payment;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentRequestController {
    private final RabbitTemplate rabbitTemplate;

    public PaymentRequestController(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostMapping("/request")
    public ResponseEntity<Map<String, Object>> requestPayment(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        if (payload.get("amount") == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Campo 'amount' é obrigatório"));
        }

        Map<String, Object> event = new LinkedHashMap<>(payload);
        String eventId = UUID.randomUUID().toString();
        event.put("eventId", eventId);
        event.put("correlationId", correlationId == null ? "" : correlationId);

        rabbitTemplate.convertAndSend(PaymentQueueConfig.PAYMENT_QUEUE, event);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "status", "PAYMENT_QUEUED",
                "eventId", eventId,
                "correlationId", correlationId == null ? "" : correlationId));
    }
}
