# Payment Service

Pagamentos **simulados** do Food Delivery (Java 21 + Spring Boot 4.1.2 + PostgreSQL + Flyway + RabbitMQ + JWT). Sobe em http://localhost:8085.

Documentação interativa (Swagger): http://localhost:8085/swagger-ui/index.html

Saúde do serviço (sem token): http://localhost:8085/actuator/health (inclui `rabbit`)

## Como funciona

O Payment não recebe pedidos de pagamento por REST: ele consome eventos do Order no RabbitMQ.

| Fila | Evento | O que faz |
|---|---|---|
| `payment.requested.queue` | `PaymentRequested` | Simula a cobrança, grava `Payment` + transação `CHARGE` e publica `PaymentApproved` ou `PaymentRejected`. Idempotente por `orderId`: repetido não cobra de novo e reenvia o mesmo resultado. |
| `payment.refund-requested.queue` | `RefundRequested` | Se o pagamento está `APPROVED`: grava transação `REFUND`, muda para `REFUNDED` e publica `PaymentRefunded`. Pagamento inexistente, recusado ou já estornado: descartado com log. |

Fluxo completo, payloads, DLQ e retry: seção **Mensageria** do [README da raiz](../README.md#mensageria).

A aprovação é aleatória, com probabilidade `PAYMENT_APPROVAL_RATE` (padrão `0.8`). Use `1.0` para aprovar tudo e `0.0` para recusar tudo.

## Banco de dados

Banco próprio `payment_db` (porta 5437 no host), criado pelo Flyway (`src/main/resources/db/migration`).

| Tabela | Conteúdo |
|---|---|
| `payments` | um pagamento por pedido (`order_id` único): valor, método, status `APPROVED`, `REJECTED` ou `REFUNDED` |
| `payment_transactions` | histórico: `CHARGE` ou `REFUND`, status `SUCCEEDED`/`FAILED`, valor, motivo e data |

## Como rodar

O `docker-compose.yml` fica só na raiz do repositório. Rode os comandos `docker compose` de lá.

```bash
# projeto inteiro
docker compose up --build

# só o Payment e o que ele usa diretamente
docker compose up --build payment-service

# só o banco e o broker em container, serviço pela IDE ou Maven (dentro de PaymentService/)
docker compose up -d payment-db rabbitmq
./mvnw spring-boot:run
```

## Configuração (variáveis de ambiente)

| Variável | Padrão | Para quê |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5437/payment_db` | Banco do Payment |
| `DB_USERNAME` / `DB_PASSWORD` | `payment` / `payment` | Usuário e senha do banco |
| `SERVER_PORT` | `8085` | Porta HTTP |
| `JWT_SECRET` | o mesmo padrão do User Service | Validar o token emitido pelo User Service |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | Broker (no compose: `rabbitmq`) |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | `guest` / `guest` | Credenciais do broker |
| `MESSAGING_ENABLED` | `true` | Com `false`, não consome filas e os eventos só vão para o log |
| `PAYMENT_APPROVAL_RATE` | `0.8` | Probabilidade de aprovar (0.0 a 1.0) |
| `RABBITMQ_LISTENER_MAX_RETRIES` | `2` | Novas tentativas antes da DLQ (1 + 2 = 3 tentativas) |
| `RABBITMQ_LISTENER_RETRY_INITIAL` | `2s` | Espera antes da 1ª nova tentativa (dobra a cada tentativa) |

## Endpoints

| Endpoint | Quem acessa | Respostas |
|---|---|---|
| `GET /payments/{id}` | cliente dono do pagamento ou `ADMIN` | 200, 401, 403, 404 |
| `GET /payments/order/{orderId}` | cliente dono do pagamento ou `ADMIN` | 200, 401, 403, 404 |
| `POST /payments/{id}/refund` (corpo opcional `{"reason": "..."}`) | `ADMIN` | 200, 401, 403, 404, 409 (recusado ou já estornado) |

O estorno via REST e o estorno via `RefundRequested` usam o mesmo `PaymentService.refund`, então seguem as mesmas regras e ambos publicam `PaymentRefunded`.

Exemplo de resposta:

```json
{
  "id": "…", "orderId": "…", "customerId": 10, "amount": 51.80, "method": "PIX", "status": "REFUNDED",
  "transactions": [
    { "id": "…", "type": "CHARGE", "status": "SUCCEEDED", "amount": 51.80, "reason": null, "createdAt": "…" },
    { "id": "…", "type": "REFUND", "status": "SUCCEEDED", "amount": 51.80, "reason": "Sem entregador", "createdAt": "…" }
  ],
  "createdAt": "…", "updatedAt": "…"
}
```

## Testes

```bash
./mvnw test
```

- `PaymentMessagingTests`: RabbitMQ e PostgreSQL reais (Testcontainers, pulado sem Docker). Cobrança aprovada/recusada, idempotência, estorno, descarte, DLQ e endpoints REST.
- `PaymentSimulatorTests` e `PaymentServiceTests`: unitários da simulação e das regras de estorno.

## Limitações

- Pagamento simulado: não há gateway, cartão nem dinheiro de verdade.
- Sem Transactional Outbox: falha do broker entre o commit e o envio perde o evento de resultado (um `PaymentRequested` repetido reenvia o resultado).
- Mensagens na DLQ não são reprocessadas automaticamente.
