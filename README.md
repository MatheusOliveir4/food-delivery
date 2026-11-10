# Food Delivery

## Arquitetura proposta

<img width="767" height="598" alt="image" src="https://github.com/user-attachments/assets/17c68fcb-ce3c-4c03-bfb2-c12198ff523d" />


## Responsabilidades de cada microsserviço

## 1. User Service

Funcionalidades:
- Cadastro de usuários.
- Login e autenticação JWT.
- Controle de permissões por perfil.
- Gerenciamento de dados pessoais.
- Cadastro e gerenciamento de endereços.

Entidades:
- `User`
- `Address`

Perfis:
- `CUSTOMER`
- `RESTAURANT_OWNER`
- `ADMIN`

Endpoints:

```
POST   /users/register
POST   /users/login
GET    /users/{id}
PUT    /users/{id}
POST   /users/{id}/addresses
GET    /users/{id}/addresses
```

## 2. Restaurant Service

Responsável pelos estabelecimentos cadastrados na plataforma.

Funcionalidades:
- Cadastro e edição de restaurantes.
- Associação do proprietário ao restaurante.
- Cadastro de horários de funcionamento.
- Controle de restaurante aberto/fechado.
- Consulta de restaurantes disponíveis.

Entidades:
- `Restaurant`
- `OperatingHours`

Endpoints:

```
POST   /restaurants
GET    /restaurants
GET    /restaurants/{id}
PUT    /restaurants/{id}
PATCH  /restaurants/{id}/availability
```

## 3. Catalog Service

Responsável pelos cardápios dos restaurantes.

Funcionalidades:
- Cadastro de categorias.
- Cadastro e edição de produtos.
- Definição de preços.
- Controle de disponibilidade dos produtos.
- Consulta de cardápios por restaurante.

Entidades:
- `Category`
- `Product`

Endpoints:

```
POST   /products
GET    /products/{id}
GET    /restaurants/{id}/products
PUT    /products/{id}
PATCH  /products/{id}/availability
```

## 4. Order Service

Aqui juntamos Cart Service e Order Service.

O carrinho não precisa necessariamente de um microsserviço próprio. Para o escopo de vocês, ele pode ser uma funcionalidade do serviço de pedidos.

Funcionalidades:

- Criar e consultar carrinhos.
- Adicionar, remover e atualizar itens.
- Calcular subtotal.
- Realizar checkout.
- Validar produtos e preços com o Catalog Service.
- Criar e consultar pedidos.
- Controlar status dos pedidos.
- Permitir que restaurantes aceitem ou rejeitem pedidos.
- Atualizar status de preparação.

Entidades:
- `Cart`
- `CartItem`
- `Order`
- `OrderItem`

Endpoints:

```
POST   /cart/items
GET    /cart/{customerId}
PUT    /cart/items/{id}
DELETE /cart/items/{id}

POST   /orders/checkout
GET    /orders/{id}
GET    /orders/customer/{customerId}
GET    /orders/restaurant/{restaurantId}

PATCH  /orders/{id}/accept
PATCH  /orders/{id}/reject
PATCH  /orders/{id}/preparation
```

Uma observação importante: ao criar o pedido, armazenem uma cópia do nome e do preço de cada produto em `OrderItem`. Assim, uma alteração futura no cardápio não modifica pedidos antigos.

## 5. Payment Service

Responsável pelo processamento dos pagamentos.

Funcionalidades:
- Receber solicitações de pagamento.
- Simular pagamento.
- Aprovar ou recusar transações.
- Consultar pagamentos.
- Simular reembolsos.

Entidades:
- `Payment`
- `PaymentTransaction`

Endpoints:

```
GET  /payments/{id}
GET  /payments/order/{orderId}
POST /payments/{id}/refund
```

O processamento inicial será feito por mensagens assíncronas, conforme explico a seguir.

## Fluxo completo do sistema

Com essa arquitetura, o fluxo principal ficaria assim:

1. Cliente realiza login pelo User Service.
2. Consulta restaurantes pelo Restaurant Service.
3. Consulta cardápios pelo Catalog Service.
4. Adiciona produtos ao carrinho pelo Order Service.
5. Solicita checkout pelo Order Service.
6. Order Service consulta Catalog e Restaurant via REST.
7. Order Service cria o pedido e publica `PaymentRequested`.
8. Payment Service processa o pagamento assincronamente.
9. Payment Service publica `PaymentApproved` ou `PaymentRejected`.
10. Order Service atualiza o pedido.
11. Restaurante consulta seus pedidos pendentes e aceita ou rejeita o pedido.
12. Restaurante atualiza a preparação para `PREPARING` e depois `READY`.

