package com.food_delivery.orderservice.messaging;

import static com.food_delivery.orderservice.messaging.PaymentTopology.DEAD_LETTER_EXCHANGE;
import static com.food_delivery.orderservice.messaging.PaymentTopology.DLQ_SUFFIX;
import static com.food_delivery.orderservice.messaging.PaymentTopology.EXCHANGE;
import static com.food_delivery.orderservice.messaging.PaymentTopology.ORDER_PAYMENT_RESULT_QUEUE;
import static com.food_delivery.orderservice.messaging.PaymentTopology.PAYMENT_APPROVED;
import static com.food_delivery.orderservice.messaging.PaymentTopology.PAYMENT_REFUNDED;
import static com.food_delivery.orderservice.messaging.PaymentTopology.PAYMENT_REJECTED;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.aopalliance.aop.Advice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.DefaultJacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitMQConfig {
    private static final Logger log = LoggerFactory.getLogger(RabbitMQConfig.class);

    /**
     * Topologia que o Order consome. A exchange e a DLX também são declaradas pelo Payment
     * (com os mesmos argumentos), então qualquer um dos dois pode subir primeiro.
     */
    @Bean
    public Declarables orderPaymentTopology() {
        TopicExchange exchange = ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
        DirectExchange deadLetterExchange = ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE).durable(true).build();

        Queue resultQueue = QueueBuilder.durable(ORDER_PAYMENT_RESULT_QUEUE)
            .deadLetterExchange(DEAD_LETTER_EXCHANGE)
            .deadLetterRoutingKey(ORDER_PAYMENT_RESULT_QUEUE + DLQ_SUFFIX)
            .build();
        Queue resultDlq = QueueBuilder.durable(ORDER_PAYMENT_RESULT_QUEUE + DLQ_SUFFIX).build();

        List<Declarable> declarables = new ArrayList<>(List.of(exchange, deadLetterExchange, resultQueue, resultDlq));
        for (String routingKey : List.of(PAYMENT_APPROVED, PAYMENT_REJECTED, PAYMENT_REFUNDED)) {
            Binding binding = BindingBuilder.bind(resultQueue).to(exchange).with(routingKey);
            declarables.add(binding);
        }
        declarables.add(BindingBuilder.bind(resultDlq).to(deadLetterExchange).with(resultDlq.getName()));

        return new Declarables(declarables);
    }

    /**
     * JSON com Jackson 3. O tipo vem sempre do parâmetro do listener e o header __TypeId__
     * não é escrito nem lido: os pacotes Java do Order e do Payment não se conhecem.
     */
    @Bean
    public MessageConverter jacksonMessageConverter(JsonMapper jsonMapper) {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(jsonMapper);
        DefaultJacksonJavaTypeMapper typeMapper = new DefaultJacksonJavaTypeMapper() {
            @Override
            public void fromJavaType(JavaType javaType, org.springframework.amqp.core.MessageProperties properties) {
                // não escreve __TypeId__
            }

            @Override
            public void fromClass(Class<?> clazz, org.springframework.amqp.core.MessageProperties properties) {
                // não escreve __TypeId__
            }
        };
        typeMapper.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.INFERRED);
        converter.setJavaTypeMapper(typeMapper);
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }

    /** Loga nack do broker e mensagens devolvidas por falta de rota (mandatory). */
    @Bean
    public RabbitTemplateCustomizer publisherConfirmsCustomizer() {
        return template -> {
            template.setConfirmCallback((correlation, ack, cause) -> {
                if (!ack) {
                    log.error("Broker não confirmou o evento {}: {}",
                        correlation != null ? correlation.getId() : "?", cause);
                }
            });
            template.setReturnsCallback(returned -> log.error(
                "Evento devolvido pelo broker (sem fila para a rota): exchange={} routingKey={} replyCode={} replyText={} messageId={}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyCode(),
                returned.getReplyText(), returned.getMessage().getMessageProperties().getMessageId()));
        };
    }

    /** Payload inválido, pedido inexistente etc. não passam pelo retry: vão direto para a DLQ. */
    @Bean
    public RabbitListenerRetrySettingsCustomizer nonRetryableErrorsCustomizer() {
        return settings -> settings.setExceptionPredicate(NonRetryableErrors::isRetryable);
    }

    /** Mesma fábrica do Spring Boot (retry, requeue etc.) + correlation id no MDC por mensagem. */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);

        List<Advice> chain = new ArrayList<>();
        chain.add(new CorrelationIdListenerAdvice());
        if (factory.getAdviceChain() != null) {
            chain.addAll(Arrays.asList(factory.getAdviceChain()));
        }
        factory.setAdviceChain(chain.toArray(Advice[]::new));
        return factory;
    }
}
