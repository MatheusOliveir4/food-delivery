# Order Service

Carrinho e pedidos do Food Delivery (Java 21 + Spring Boot 4.1.2 + PostgreSQL + Flyway + JWT). Sobe em http://localhost:8084.

Documentação interativa (Swagger): http://localhost:8084/swagger-ui/index.html

Saúde do serviço (sem token): http://localhost:8084/actuator/health

## Banco de dados

O Order tem o **próprio** PostgreSQL (`order_db`, porta 5436 no host). Nenhum outro serviço acessa esse banco, e o Order não acessa o banco de ninguém: produtos e restaurantes são consultados só pela API REST do Catalog e do Restaurant. Por isso as tabelas guardam apenas os ids (`product_id`, `restaurant_id`, `customer_id`), sem chave estrangeira para outros serviços.

| Serviço | Banco | Porta no host |
|---|---|---|
| User | `user_db` | 5433 |
| Catalog | `catalog_db` | 5434 |
| Restaurant | `restaurant_db` | 5435 |
| **Order** | **`order_db`** | **5436** |

As tabelas (`carts`, `cart_items`, `orders`, `order_items`) são criadas pelo Flyway a partir de `src/main/resources/db/migration`. O Hibernate só valida o mapeamento (`ddl-auto: validate`); qualquer mudança de tabela deve ser uma nova migração (`V2__...sql`).

## Como rodar

O `docker-compose.yml` fica só na raiz do repositório. Rode os comandos `docker compose` de lá.

```bash
# projeto inteiro (todos os serviços, bancos, RabbitMQ e observabilidade)
docker compose up --build

# só o Order e o que ele usa diretamente
docker compose up --build order-service catalog-service restaurant-service

# só o banco e o broker em container, Order pela IDE ou Maven (dentro de OrderService/)
docker compose up -d order-db rabbitmq
mvn spring-boot:run

# sem PostgreSQL e sem Catalog/Restaurant: H2 em memória e dados falsos.
# A mensageria fica desligada no profile stub; use /dev/payments/* para aprovar/recusar.
mvn spring-boot:run -Dspring-boot.run.profiles=stub

# stub, mas com RabbitMQ (precisa do broker: docker compose up -d rabbitmq)
MESSAGING_ENABLED=true mvn spring-boot:run -Dspring-boot.run.profiles=stub
```

No `docker compose`, o Order chama o Catalog e o Restaurant **através do middleware** (`http://middleware:8080`), que aplica timeout, retry, circuit breaker e correlation ID. Rodando pela IDE, o padrão é chamar os serviços direto (`localhost:8083` e `localhost:8082`). Para outro endereço, defina `CATALOG_SERVICE_URL` e `RESTAURANT_SERVICE_URL`.

## Configuração (variáveis de ambiente)

| Variável | Padrão | Para quê |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5436/order_db` | Banco do Order |
| `DB_USERNAME` / `DB_PASSWORD` | `order` / `order` | Usuário e senha do banco |
| `SERVER_PORT` | `8084` | Porta HTTP do serviço |
| `CATALOG_SERVICE_URL` | `http://localhost:8083` | Endereço do Catalog Service |
| `RESTAURANT_SERVICE_URL` | `http://localhost:8082` | Endereço do Restaurant Service |
| `JWT_SECRET` | o mesmo padrão do User Service | Validar o token emitido pelo User Service |
| `HTTP_CONNECT_TIMEOUT` | `2s` | Tempo máximo para conectar ao Catalog e ao Restaurant |
| `HTTP_READ_TIMEOUT` | `5s` | Tempo máximo de espera pela resposta do Catalog e do Restaurant |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | Broker RabbitMQ (no compose: `rabbitmq`) |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | `guest` / `guest` | Credenciais do broker |
| `MESSAGING_ENABLED` | `true` (`false` no profile `stub`) | Publica/consome eventos no RabbitMQ. Com `false`, os eventos só vão para o log |
| `RABBITMQ_LISTENER_MAX_RETRIES` | `2` | Novas tentativas do listener antes da DLQ (1 + 2 = 3 tentativas) |
| `RABBITMQ_LISTENER_RETRY_INITIAL` | `2s` | Espera antes da 1ª nova tentativa (dobra a cada tentativa) |
| `PAYMENT_SIMULATOR_ENABLED` | `false` (`true` no profile `stub`) | Liga `/dev/payments/*` para aprovar/recusar pagamentos à mão |
| `ORDER_PROFILES` (só no compose) | vazio | `stub` sobe o Order com H2 e Catalog/Restaurant falsos |

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
| `POST /dev/payments/{orderId}/approve` e `/reject` | só com `PAYMENT_SIMULATOR_ENABLED=true` (profile `stub`) |

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
| Payment | RabbitMQ (`payment.exchange`) | publica `PaymentRequested`, `RefundRequested`; consome `PaymentApproved`, `PaymentRejected`, `PaymentRefunded` |

Detalhes (filas, payloads, DLQ, retry) na seção **Mensageria** do [README da raiz](../README.md#mensageria).

- `RabbitPaymentEventPublisher` publica depois do commit do checkout/rejeição. Com `MESSAGING_ENABLED=false`, `LoggingPaymentEventPublisher` só registra os eventos no log.
- `PaymentResultListener` consome `order.payment-result.queue`: `PaymentApproved` → `PENDING_RESTAURANT`, `PaymentRejected` → `PAYMENT_REJECTED`, `PaymentRefunded` → só log (não existe `REFUNDED` em `OrderStatus`; o pedido continua `REJECTED`).

## Correlation ID

Toda requisição recebe um `X-Correlation-ID`. Se o cliente enviar um válido (letras, números, `.`, `_`, `-`, até 100 caracteres), ele é reaproveitado; senão, o Order gera um UUID. O ID:

- volta no cabeçalho da resposta, inclusive em respostas de erro;
- aparece entre colchetes em cada linha de log da requisição;
- é repassado no cabeçalho `X-Correlation-ID` das chamadas ao Catalog e ao Restaurant;
- vai no header `X-Correlation-ID` dos eventos publicados no RabbitMQ, e o listener coloca o valor recebido no log enquanto processa a mensagem.

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
| Catalog fora do ar ou lento | Adicionar item e checkout respondem `503` após no máximo `HTTP_CONNECT_TIMEOUT` (sem conexão) ou `HTTP_READ_TIMEOUT` (sem resposta). Nada é gravado: o carrinho e os pedidos ficam como estavam. Consultas de carrinho e pedidos continuam funcionando, porque usam só o banco do Order. |
| Restaurant fora do ar ou lento | Checkout e ações do restaurante (listar, aceitar, rejeitar, preparar) respondem `503`. Nada é alterado. Cliente continua consultando carrinho e pedidos. |
| Produto removido ou indisponível depois de ir para o carrinho | O checkout revalida tudo no Catalog e responde `422` sem criar o pedido. |
| Preço alterado no Catalog depois de ir para o carrinho | O checkout usa o preço atual do Catalog. Depois de criado, o pedido guarda cópia de nome e preço e não muda mais. |
| Resultado de pagamento repetido | Ignorado: só é aplicado a pedidos em `PENDING_PAYMENT` (idempotência). |
| Pagamento nunca responde | O pedido fica em `PENDING_PAYMENT` (ainda não há expiração). |
| RabbitMQ fora do ar | O serviço sobe e fica tentando reconectar; `/actuator/health` mostra `rabbit: DOWN`. O checkout grava o pedido, mas o `PaymentRequested` se perde (erro no log, ver limitações). |
| Evento inválido, ou para pedido que não existe | Vai direto para `order.payment-result.queue.dlq`, sem novas tentativas. |
| Erro temporário ao processar evento (ex.: banco) | 3 tentativas (2s, 4s); depois vai para a DLQ. |
| Banco do Order fora do ar | O serviço não sobe; se cair com o serviço rodando, as requisições respondem `500` e `/actuator/health` fica `DOWN`. Os outros serviços não são afetados. |

**Consistência:** entre o carrinho/pedido e os outros serviços a consistência é **forte no momento do checkout** (o Order consulta Catalog e Restaurant e só grava se tudo estiver válido). Entre o pedido e o pagamento ela é **eventual**: o pedido nasce em `PENDING_PAYMENT` e só muda quando o resultado do pagamento chega.

## Limitações conhecidas

- Os eventos são publicados **depois** do commit, mas sem Transactional Outbox: se o broker falhar entre o commit e o envio, o evento se perde (fica um `ERROR ... EVENTO PERDIDO` no log e o pedido fica em `PENDING_PAYMENT`).
- Mensagens na DLQ não são reprocessadas automaticamente (reenvie pelo painel do RabbitMQ).
