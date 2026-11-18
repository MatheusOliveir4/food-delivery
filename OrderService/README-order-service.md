# Order Service

Carrinho e pedidos do Food Delivery (Java 21 + Spring Boot 4.1.2 + H2 + JWT). Sobe em http://localhost:8084.

## Como rodar

```bash
# integrado: chama Catalog (8083) e Restaurant (8082) via REST
mvn spring-boot:run

# sozinho: Catalog e Restaurant falsos, e endpoints /dev/payments para simular o pagamento
mvn spring-boot:run -Dspring-boot.run.profiles=stub
```

O JWT é o emitido pelo User Service. Use o mesmo `JWT_SECRET` nos dois serviços.

## Endpoints

| Endpoint | Quem acessa |
|---|---|
| `POST /cart/items` | `CUSTOMER` (o cliente vem do token) |
| `GET /cart/{customerId}` | o próprio cliente ou `ADMIN` |
| `PUT /cart/items/{id}`, `DELETE /cart/items/{id}` | `CUSTOMER` (só itens do próprio carrinho) |
| `POST /orders/checkout` | `CUSTOMER` |
| `GET /orders/{id}` | dono do pedido, `RESTAURANT_OWNER` ou `ADMIN` |
| `GET /orders/customer/{customerId}` | o próprio cliente ou `ADMIN` |
| `GET /orders/restaurant/{restaurantId}[?status=]` | `RESTAURANT_OWNER` ou `ADMIN` |
| `PATCH /orders/{id}/accept`, `/reject`, `/preparation` | `RESTAURANT_OWNER` ou `ADMIN` |

## Status do pedido

```
PENDING_PAYMENT ──PaymentApproved──> PENDING_RESTAURANT ──accept──> ACCEPTED ──> PREPARING ──> READY
       │                                      │
       └──PaymentRejected──> PAYMENT_REJECTED └──reject──> REJECTED (pede reembolso)
```

Transições fora desse fluxo devolvem `409`.

## Integração com os outros serviços

| Serviço | Como | Contrato esperado |
|---|---|---|
| Catalog | REST `GET /products/{id}` | `id`, `name`, `price`, `available`, `restaurantId` |
| Restaurant | REST `GET /restaurants/{id}` | `id`, `name`, `open` |
| Payment | eventos (interface `PaymentEventPublisher`) | `PaymentRequested`, `RefundRequested` |

Ainda não há broker: `LoggingPaymentEventPublisher` só registra os eventos no log.
Para integrar, crie uma implementação de `PaymentEventPublisher` que publique no broker escolhido
e chame `OrderService.handlePaymentApproved(orderId)` / `handlePaymentRejected(orderId, reason)`
ao consumir `PaymentApproved` / `PaymentRejected`.

## Limitações conhecidas

- O `id` do usuário (JWT) é `Long`; Restaurant e Catalog usam `UUID`. O Order guarda `customerId` como `Long`.
- Não verifica se o `RESTAURANT_OWNER` é dono do restaurante do pedido (o `ownerId` do Restaurant Service é `UUID`).
- Os eventos são publicados dentro da transação do checkout (sem outbox).
