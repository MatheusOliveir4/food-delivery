# Middleware

Camada de comunicação do Food Delivery (Java 21 + Spring Boot 4.1.2), escrita do zero pelo grupo. Toda comunicação síncrona passa por ela: a do cliente com os serviços e a do Order com o Catalog e o Restaurant. A comunicação assíncrona (Order ↔ Payment) continua no RabbitMQ.

Sobe em http://localhost:8080.

```
Cliente ──► middleware :8080 ──► User | Restaurant | Catalog | Order | Payment
                 ▲                                              │
                 └──────── Order chama Catalog e Restaurant ────┘
```

Não usa gateway pronto (Kong, Spring Cloud Gateway etc.) nem biblioteca de resiliência (Resilience4j etc.). Usa só o Spring Boot (framework REST), o `HttpClient` do Java (driver HTTP), o jjwt (leitura do token) e o Micrometer (publicação das métricas).

## O que ele faz em cada requisição

1. **Correlation ID:** reaproveita o `X-Correlation-ID` recebido ou gera um UUID. O ID vai no log, na resposta e no cabeçalho da chamada ao serviço.
2. **Roteamento:** procura a primeira rota que combina com o método e o caminho (tabela em `application.yml`, `middleware.routes`).
3. **Autenticação e autorização:** valida o JWT emitido pelo User Service (mesmo `JWT_SECRET`) e o papel exigido pela rota. Sem token: `401`. Papel errado: `403`.
4. **Encaminhamento:** repassa método, caminho, query, cabeçalhos e corpo; devolve status, cabeçalhos e corpo do serviço.
5. **Resiliência:**
   - **timeout** de conexão (1 s) e de resposta (3 s; 10 s para o Order, que chama outros serviços através do middleware);
   - **retry com backoff exponencial** (3 tentativas, 200 ms e 400 ms) só em `GET`/`HEAD`, quando o serviço não aceita conexão ou responde 502, 503 ou 504;
   - **circuit breaker** por serviço: abre após 5 falhas seguidas, fica aberto 10 s, depois deixa passar uma chamada de teste (meio-aberto) e fecha se ela der certo;
   - **fallback:** com o circuito aberto, responde `503` na hora, sem chamar o serviço.
6. **Métricas e log:** cada requisição gera uma linha de log em JSON e entra nas métricas.
7. **Tracing:** cada chamada a um serviço gera um span filho do span da requisição recebida, e o cabeçalho `traceparent` (W3C) é escrito pelo middleware. O `traceparent` que veio do cliente não é repassado como está, para a árvore no Zipkin ficar cliente → middleware → serviço.

## Rotas

Os caminhos são os mesmos de cada serviço; só a porta muda para 8080.

| Rota | Serviço | Acesso |
|---|---|---|
| `POST /users/register`, `POST /users/login` | User | público |
| `/users/**` | User | token |
| `GET /restaurants/*/menu` | Catalog | público |
| `GET /restaurants/**` | Restaurant | público |
| demais métodos em `/restaurants/**` | Restaurant | `RESTAURANT_OWNER` ou `ADMIN` |
| `GET /products/**`, `GET /categories/**` | Catalog | público |
| demais métodos em `/products/**`, `/categories/**` | Catalog | `RESTAURANT_OWNER` ou `ADMIN` |
| `/cart/**`, `/orders/**`, `/dev/payments/**` | Order | token (o Order também confere o papel) |
| `/payments/**` | Payment | token (o Payment também confere o papel) |

Rota não cadastrada: `404`.

## Endpoints próprios

| Endpoint | Para quê |
|---|---|
| `GET /middleware/health` | Situação de cada serviço (`UP`, `DOWN`, `UNREACHABLE`) e estado do circuit breaker |
| `GET /middleware/routes` | Tabela de rotas carregada |
| `GET /actuator/health` | Saúde do próprio middleware |
| `GET /actuator/prometheus` | Métricas |

## Métricas

| Métrica | O que mede |
|---|---|
| `middleware_requests_seconds_*` | Requisições recebidas, por `service`, `route`, `method`, `status` e `outcome` (contagem, latência e histograma) |
| `middleware_downstream_requests_seconds_*` | Chamadas aos serviços, por `service` e `result` (status, `TIMEOUT` ou `UNAVAILABLE`) |
| `middleware_retries_total` | Novas tentativas por serviço |
| `middleware_circuit_state` | Estado do circuito: 0 = `CLOSED`, 1 = `HALF_OPEN`, 2 = `OPEN` |
| `middleware_circuit_rejections_total` | Requisições recusadas porque o circuito estava aberto |

No Grafana: **Dashboards → Food Delivery → Food Delivery - Middleware**.

## Respostas de erro

Mesmo formato dos serviços: `{"timestamp": "...", "status": 503, "error": "..."}`.

| Status | Quando |
|---|---|
| 401 | Rota exige token e ele falta ou é inválido |
| 403 | Papel sem permissão para a rota |
| 404 | Nenhuma rota combina com o método e o caminho |
| 503 | Serviço não aceita conexão (depois das tentativas) ou circuito aberto |
| 504 | Serviço não respondeu dentro do timeout |

Erros gerados pelo próprio serviço (400, 404, 409, 422 etc.) são repassados sem alteração.

## Comportamento em falhas

| Situação | Comportamento |
|---|---|
| Um serviço cai | As chamadas a ele dão `503` (com retry nos `GET`). Depois de 5 falhas seguidas o circuito abre e as chamadas falham na hora. Os outros serviços seguem normais. |
| O serviço volta | Depois de 10 s, uma chamada de teste passa; se der certo, o circuito fecha. |
| Serviço lento | `504` depois do timeout de resposta. Conta como falha para o circuito, mas não tem retry (para não sobrecarregar um serviço já lento). |
| O middleware cai | Toda comunicação síncrona para: ele é ponto único de entrada. Não guarda estado, então o `restart: on-failure` do Docker o religa sem perda. A mensageria (Order ↔ Payment) continua funcionando, porque não passa por ele. |

## Configuração (variáveis de ambiente)

| Variável | Padrão |
|---|---|
| `USER_SERVICE_URL` … `PAYMENT_SERVICE_URL` | `http://localhost:8081` … `http://localhost:8085` (no compose: nomes dos containers) |
| `JWT_SECRET` | o mesmo padrão do User Service |
| `MIDDLEWARE_CONNECT_TIMEOUT` / `MIDDLEWARE_READ_TIMEOUT` | `1s` / `3s` |
| `MIDDLEWARE_ORDER_READ_TIMEOUT` | `10s` |
| `MIDDLEWARE_RETRY_MAX_ATTEMPTS` / `MIDDLEWARE_RETRY_INITIAL_BACKOFF` | `3` / `200ms` |
| `MIDDLEWARE_CB_FAILURE_THRESHOLD` / `MIDDLEWARE_CB_OPEN_DURATION` | `5` / `10s` |
| `LOG_FORMAT` | `ecs` (log em JSON) |
| `ZIPKIN_ENDPOINT` | `http://localhost:9411/api/v2/spans` (no compose: `http://zipkin:9411/api/v2/spans`) |
| `TRACING_SAMPLING_PROBABILITY` | `1.0` (todas as requisições) |

## Como rodar

Na raiz do repositório:

```bash
docker compose up -d --build                    # projeto inteiro, middleware em :8080
docker compose logs -f middleware               # log em JSON
curl localhost:8080/middleware/health
```

Testes (dentro de `middleware/`): `mvn test`. Usam serviços falsos em portas locais e cobrem roteamento, correlation ID, 401/403, retry, timeout e abertura do circuito.
