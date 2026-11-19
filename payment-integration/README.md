# Módulo de Payment para Food Delivery

Este pacote contém somente o consumidor de pagamentos (`payment-worker`) e os dois arquivos mínimos que precisam ser adicionados ao Middleware para publicar solicitações na fila RabbitMQ.

## Como as partes se conectam

1. O cliente chama `POST /api/v1/payments/request` no Middleware.
2. `PaymentRequestController` cria `eventId`, preserva `orderId`/`amount` e inclui `correlationId`.
3. O Middleware publica o evento na fila durável `payment.requested`.
4. O `payment-worker`, processo independente, consome a fila e registra o resultado simulado.

O User Service e o Catalog Service não precisam ser alterados para esta integração. O Middleware é o ponto de entrada/orquestração entre o cliente e o processamento assíncrono.

## Instalação no repositório clonado

- Copie a pasta `payment-worker/` para a raiz do repositório `food-delivery`.
- Copie os dois arquivos de `middleware-payment-integration/src/main/java/.../payment/` para o projeto `middleware-service`, mantendo o pacote/caminho.
- No `pom.xml` do Middleware, confirme que existe a dependência `spring-boot-starter-amqp`.
- No `application.yml` do Middleware, configure `spring.rabbitmq.host`, `port`, `username` e `password` (variáveis `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`).
- O RabbitMQ precisa estar ativo e acessível pelos dois serviços. O Middleware declara a fila durável; o Worker consome a mesma fila.

## Docker Compose

No `docker-compose.yml` existente, adicione o serviço abaixo e conecte-o à mesma network do RabbitMQ e Middleware:

```yaml
  payment-worker:
    build: ./payment-worker
    environment:
      RABBITMQ_HOST: rabbitmq
      RABBITMQ_PORT: 5672
      RABBITMQ_USER: guest
      RABBITMQ_PASSWORD: guest
    depends_on:
      rabbitmq:
        condition: service_healthy
    restart: unless-stopped
```

O serviço pressupõe que o container do broker se chama `rabbitmq`. Se no Compose ele tiver outro nome, ajuste `RABBITMQ_HOST` para o nome real do serviço. Se o Compose não tiver healthcheck no RabbitMQ, remova `condition: service_healthy` e use apenas `depends_on: [rabbitmq]`.

## Teste

Com Middleware e RabbitMQ em execução, envie:

```bash
curl -X POST http://localhost:8080/api/v1/payments/request \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: teste-pagamento-001" \
  -d '{"orderId":"pedido-123","amount":49.90,"currency":"BRL","method":"PIX"}'
```

Resposta esperada: HTTP `202 Accepted` com `status: PAYMENT_QUEUED`, `eventId` e `correlationId`. Nos logs do `payment-worker`, procure `payment_received` e `payment_processed`.

## Limites desta implementação

O pagamento é **simulado**. Não há integração com gateway/banco, captura de cartão, persistência de transações, estorno, autenticação específica do endpoint, idempotência ou dead-letter queue. O consumidor valida `amount` e registra `APPROVED` apenas para demonstrar o fluxo assíncrono. Não use em produção.
