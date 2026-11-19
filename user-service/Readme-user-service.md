# User Service

Microsserviço de usuários do projeto Food Delivery (Java 17 + Spring Boot 3 + PostgreSQL + JWT).

## Como rodar

O `docker-compose.yml` fica só na raiz do repositório.

```bash
# na raiz: só o User e o banco dele
docker compose up --build user-service

# ou só o banco em container e o serviço pela IDE ou Maven (dentro de user-service/)
docker compose up -d user-db
mvn spring-boot:run
```

Sobe em http://localhost:8081. Um ADMIN inicial é criado automaticamente
(`admin@fooddelivery.com` / `admin12345`, configurável por variáveis de ambiente).

## Testando com curl

```bash
# cadastro
curl -X POST localhost:8081/users/register -H "Content-Type: application/json" \
  -d '{"name":"Maria","email":"maria@email.com","password":"12345678","phone":"81999999999","role":"CUSTOMER"}'

# login (devolve o token)
curl -X POST localhost:8081/users/login -H "Content-Type: application/json" \
  -d '{"email":"maria@email.com","password":"12345678"}'

# buscar usuário (troque TOKEN e o id)
curl localhost:8081/users/2 -H "Authorization: Bearer TOKEN"

# adicionar endereço
curl -X POST localhost:8081/users/2/addresses -H "Authorization: Bearer TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"street":"Rua das Flores","number":"100","neighborhood":"Boa Viagem","city":"Recife","state":"PE","zipCode":"51020-000","main":true}'
```

## Regras

| Endpoint | Quem acessa |
|---|---|
| `POST /users/register` | público (só `CUSTOMER` ou `RESTAURANT_OWNER`) |
| `POST /users/login` | público |
| `GET/PUT /users/{id}` | o próprio usuário ou `ADMIN` |
| `POST/GET /users/{id}/addresses` | o próprio usuário ou `ADMIN` |

O token JWT leva `sub` (id do usuário), `email` e `role`. Os outros serviços
podem validar o token com o mesmo `JWT_SECRET`.
