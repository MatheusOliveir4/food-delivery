package com.food_delivery.paymentservice.messaging;

import static com.food_delivery.paymentservice.messaging.PaymentTopology.DEAD_LETTER_EXCHANGE;
import static com.food_delivery.paymentservice.messaging.PaymentTopology.DLQ_SUFFIX;
import static com.food_delivery.paymentservice.messaging.PaymentTopology.EXCHANGE;
import static com.food_delivery.paymentservice.messaging.PaymentTopology.PAYMENT_REQUESTED;
import static com.food_delivery.paymentservice.messaging.PaymentTopology.PAYMENT_REQUESTED_QUEUE;
import static com.food_delivery.paymentservice.messaging.PaymentTopology.REFUND_REQUESTED;
import static com.food_delivery.paymentservice.messaging.PaymentTopology.REFUND_REQUESTED_QUEUE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.aopalliance.aop.Advice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListenerConfigurer;
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
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitMQConfig {
    private static final Logger log = LoggerFactory.getLogger(RabbitMQConfig.class);

    /**
     * Topologia que o Payment consome. A exchange e a DLX também são declaradas pelo Order
     * (com os mesmos argumentos), então qualquer um dos dois pode subir primeiro.
     */
    @Bean
    public Declarables paymentTopology() {
        TopicExchange exchange = ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
        DirectExchange deadLetterExchange = ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE).durable(true).build();

        List<Declarable> declarables = new ArrayList<>(List.of(exchange, deadLetterExchange));
        declareQueue(declarables, exchange, deadLetterExchange, PAYMENT_REQUESTED_QUEUE, PAYMENT_REQUESTED);
        declareQueue(declarables, exchange, deadLetterExchange, REFUND_REQUESTED_QUEUE, REFUND_REQUESTED);

        return new Declarables(declarables);
    }

    private static void declareQueue(List<Declarable> declarables, TopicExchange exchange,
                                     DirectExchange deadLetterExchange, String name, String routingKey) {
        Queue queue = QueueBuilder.durable(name)
            .deadLetterExchange(DEAD_LETTER_EXCHANGE)
            .deadLetterRoutingKey(name + DLQ_SUFFIX)
            .build();
        Queue dlq = QueueBuilder.durable(name + DLQ_SUFFIX).build();

        declarables.add(queue);
        declarables.add(dlq);
        declarables.add(BindingBuilder.bind(queue).to(exchange).with(routingKey));
        declarables.add(BindingBuilder.bind(dlq).to(deadLetterExchange).with(dlq.getName()));
    }

    /** Valida os parâmetros {@code @Valid @Payload} dos listeners com o Bean Validation do Spring. */
    @Bean
    public RabbitListenerConfigurer rabbitListenerValidation(LocalValidatorFactoryBean validator) {
        return registrar -> registrar.setValidator(validator);
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

    /** Payload inválido ou que falha na validação não passa pelo retry: vão direto para a DLQ. */
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
