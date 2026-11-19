# Order Service

Carrinho e pedidos do Food Delivery (Java 21 + Spring Boot 4.1.2 + PostgreSQL + Flyway + JWT). Sobe em http://localhost:8084.

Documentação interativa (Swagger): http://localhost:8084/swagger-ui/index.html

Saúde do serviço (sem token): http://localhost:8084/actuator/health

## Banco de dados

O Order tem o **próprio** PostgreSQL (`order_db`, porta 5436 no host). Nenhum outro serviço acessa esse banco, e o Order não acessa o banco de ninguém: produtos e restaurantes são consultados só pela API REST do Catalog e do Restaurant. Por isso as tabelas guardam apenas os ids (`product_id`, `restaurant_id`, `customer_id`), sem chave estrangeira para outros serviços.

| Serviço | Banco | Porta no host |
|---|---|---|
| User | `user_db` | 5432 |
| Catalog | `catalog_db` | 5434 |
| Restaurant | `restaurant_db` | 5435 |
| **Order** | **`order_db`** | **5436** |

As tabelas (`carts`, `cart_items`, `orders`, `order_items`) são criadas pelo Flyway a partir de `src/main/resources/db/migration`. O Hibernate só valida o mapeamento (`ddl-auto: validate`); qualquer mudança de tabela deve ser uma nova migração (`V2__...sql`).

## Como rodar

```bash
# banco + serviço em containers
docker compose up --build

# só o banco em container, serviço pela IDE ou Maven
docker compose up -d order-db
mvn spring-boot:run

# sem PostgreSQL e sem Catalog/Restaurant: H2 em memória e dados falsos
mvn spring-boot:run -Dspring-boot.run.profiles=stub
```

No `docker compose`, o Order chama Catalog (8083) e Restaurant (8082) rodando na máquina host. Para outro endereço, defina `CATALOG_SERVICE_URL` e `RESTAURANT_SERVICE_URL` antes de subir.

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

## Correlation ID

Toda requisição recebe um `X-Correlation-ID`. Se o cliente enviar um válido (letras, números, `.`, `_`, `-`, até 100 caracteres), ele é reaproveitado; senão, o Order gera um UUID. O ID:

- volta no cabeçalho da resposta, inclusive em respostas de erro;
- aparece entre colchetes em cada linha de log da requisição;
- é repassado no cabeçalho `X-Correlation-ID` das chamadas ao Catalog e ao Restaurant.

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
| Banco do Order fora do ar | O serviço não sobe; se cair com o serviço rodando, as requisições respondem `500` e `/actuator/health` fica `DOWN`. Os outros serviços não são afetados. |

**Consistência:** entre o carrinho/pedido e os outros serviços a consistência é **forte no momento do checkout** (o Order consulta Catalog e Restaurant e só grava se tudo estiver válido). Entre o pedido e o pagamento ela é **eventual**: o pedido nasce em `PENDING_PAYMENT` e só muda quando o resultado do pagamento chega.

## Limitações conhecidas

- Os eventos são publicados dentro da transação do checkout (sem outbox).
