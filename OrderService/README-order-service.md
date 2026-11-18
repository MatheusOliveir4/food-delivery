# Order Service

Carrinho e pedidos do Food Delivery (Java 21 + Spring Boot 4.1.2 + H2 + JWT). Sobe em http://localhost:8084.

Documentação interativa (Swagger): http://localhost:8084/swagger-ui/index.html

## Como rodar

```bash
# integrado: chama Catalog (8080) e Restaurant (8082) via REST
mvn spring-boot:run

# sozinho: Catalog e Restaurant falsos
mvn spring-boot:run -Dspring-boot.run.profiles=stub
```

Com Docker:

```bash
docker build -t order-service .
docker run -p 8084:8084 \
  -e CATALOG_SERVICE_URL=http://host.docker.internal:8080 \
  -e RESTAURANT_SERVICE_URL=http://host.docker.internal:8082 \
  -e JWT_SECRET=<mesmo segredo do User Service> \
  order-service
```

## Configuração (variáveis de ambiente)

| Variável | Padrão | Para quê |
|---|---|---|
| `CATALOG_SERVICE_URL` | `http://localhost:8080` | Endereço do Catalog Service |
| `RESTAURANT_SERVICE_URL` | `http://localhost:8082` | Endereço do Restaurant Service |
| `JWT_SECRET` | o mesmo padrão do User Service | Validar o token emitido pelo User Service |
| `SERVICE_TIMEOUT_MS` | `3000` | Tempo máximo de conexão e de resposta nas chamadas ao Catalog e ao Restaurant |
| `PAYMENT_SIMULATOR_ENABLED` | `true` | Liga `/dev/payments/*` enquanto o Payment Service não existe |

## Endpoints

| Endpoint | Quem acessa |
|---|---|
| `POST /cart/items` | `CUSTOMER` (o cliente vem do token) |
| `GET /cart/{customerId}` | o próprio cliente ou `ADMIN` |
| `PUT /cart/items/{id}`, `DELETE /cart/items/{id}` | `CUSTOMER` (só itens do próprio carrinho) |
| `POST /orders/checkout` | `CUSTOMER` |
| `GET /orders/{id}` | dono do pedido, dono do restaurante ou `ADMIN` |
| `GET /orders/customer/{customerId}` | o próprio cliente ou `ADMIN` |
| `GET /orders/restaurant/{restaurantId}[?status=]` | dono do restaurante ou `ADMIN` |
| `PATCH /orders/{id}/accept`, `/reject`, `/preparation` | dono do restaurante ou `ADMIN` |
| `POST /dev/payments/{orderId}/approve` e `/reject` | simulação do Payment Service |

"Dono do restaurante" é conferido no Restaurant Service: o `ownerId` do restaurante precisa ser o id do usuário do token.

## Status do pedido

```
PENDING_PAYMENT ──PaymentApproved──> PENDING_RESTAURANT ──accept──> ACCEPTED ──> PREPARING ──> READY
       │                                      │
       └──PaymentRejected──> PAYMENT_REJECTED └──reject──> REJECTED (pede reembolso)
```

Transições fora desse fluxo devolvem `409`.

## Integração com os outros serviços

| Serviço | Como | Contrato usado |
|---|---|---|
| Catalog | REST `GET /products/{id}` | `id`, `name`, `price`, `available`, `restaurantId` |
| Restaurant | REST `GET /restaurants/{id}` | `id`, `name`, `ownerId`, `open` |
| Payment | eventos (interface `PaymentEventPublisher`) | `PaymentRequested`, `RefundRequested` |

Ainda não há broker: `LoggingPaymentEventPublisher` só registra os eventos no log.
Para integrar, crie uma implementação de `PaymentEventPublisher` que publique no broker escolhido,
chame `OrderService.handlePaymentApproved(orderId)` / `handlePaymentRejected(orderId, reason)`
ao consumir `PaymentApproved` / `PaymentRejected` e desligue o simulador (`PAYMENT_SIMULATOR_ENABLED=false`).

## Tratamento de erros

Toda resposta de erro tem o mesmo formato:

```json
{ "timestamp": "2026-11-18T19:05:38", "status": 404, "error": "Pedido não encontrado: ..." }
```

Erros de validação trazem `errors` com a mensagem de cada campo, no lugar de `error`.

| Status | Quando |
|---|---|
| 400 | Campo inválido, JSON malformado, id ou parâmetro em formato errado |
| 401 | Sem token, token inválido ou expirado |
| 403 | Papel sem permissão, recurso de outro cliente ou restaurante de outro dono |
| 404 | Pedido, item, produto ou restaurante não encontrado, ou rota inexistente |
| 405 | Método HTTP não suportado na rota |
| 409 | Transição de status não permitida (ex.: aceitar antes do pagamento) |
| 415 | `Content-Type` diferente de `application/json` |
| 422 | Regra de negócio: carrinho vazio, produto indisponível, restaurante fechado, itens de restaurantes diferentes |
| 500 | Erro inesperado (registrado no log; o detalhe não é exposto) |
| 503 | Catalog ou Restaurant fora do ar, com erro ou sem responder dentro do timeout |

## Comportamento em falhas

| Situação | Comportamento do Order |
|---|---|
| Catalog fora do ar ou lento | Adicionar item e checkout respondem `503` após no máximo `SERVICE_TIMEOUT_MS`. Nada é gravado: o carrinho e os pedidos ficam como estavam. Consultas de carrinho e pedidos continuam funcionando, porque usam só o banco do Order. |
| Restaurant fora do ar ou lento | Checkout e ações do restaurante (listar, aceitar, rejeitar, preparar) respondem `503`. Nada é alterado. Cliente continua consultando carrinho e pedidos. |
| Produto removido ou indisponível depois de ir para o carrinho | O checkout revalida tudo no Catalog e responde `422` sem criar o pedido. |
| Preço alterado no Catalog depois de ir para o carrinho | O checkout usa o preço atual do Catalog. Depois de criado, o pedido guarda cópia de nome e preço e não muda mais. |
| Resultado de pagamento repetido | Ignorado: só é aplicado a pedidos em `PENDING_PAYMENT` (idempotência). |
| Pagamento nunca responde | O pedido fica em `PENDING_PAYMENT` (ainda não há expiração). |

**Consistência:** entre o carrinho/pedido e os outros serviços a consistência é **forte no momento do checkout** (o Order consulta Catalog e Restaurant e só grava se tudo estiver válido). Entre o pedido e o pagamento ela é **eventual**: o pedido nasce em `PENDING_PAYMENT` e só muda quando o resultado do pagamento chega.

## Limitações conhecidas

- Os eventos são publicados dentro da transação do checkout (sem outbox).
