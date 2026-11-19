package com.food_delivery.orderservice.messaging;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lê o corpo JSON de uma mensagem como o record pedido e valida os campos obrigatórios.
 * Qualquer problema vira {@link InvalidMessageException} (DLQ sem retry).
 */
@Component
public class EventReader {
    private final JsonMapper jsonMapper;
    private final Validator validator;

    public EventReader(JsonMapper jsonMapper, Validator validator) {
        this.jsonMapper = jsonMapper;
        this.validator = validator;
    }

    public <T> T read(Message message, Class<T> type) {
        T event;
        try {
            event = jsonMapper.readValue(message.getBody(), type);
        } catch (JacksonException e) {
            throw new InvalidMessageException(
                "JSON inválido para " + type.getSimpleName() + ": " + e.getOriginalMessage(), e);
        }

        if (event == null) {
            throw new InvalidMessageException("Mensagem vazia para " + type.getSimpleName());
        }

        Set<ConstraintViolation<T>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            String fields = violations.stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .sorted()
                .collect(Collectors.joining(", "));
            throw new InvalidMessageException(type.getSimpleName() + " inválido: " + fields);
        }

        return event;
    }
}
