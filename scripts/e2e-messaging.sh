#!/usr/bin/env bash
# Teste ponta a ponta da mensageria Order <-> Payment com os containers do docker-compose da raiz.
#
#   ORDER_PROFILES=stub docker compose up -d --build   # Order com Catalog/Restaurant falsos
#   ./scripts/e2e-messaging.sh
#
# Faz: carrinho -> checkout -> espera o pagamento (aprovado ou recusado) -> se aprovado,
# o restaurante rejeita -> confere o estorno no Payment Service.
set -euo pipefail

ORDER_URL=${ORDER_URL:-http://localhost:8084}
PAYMENT_URL=${PAYMENT_URL:-http://localhost:8085}
JWT_SECRET=${JWT_SECRET:-troque-esta-chave-por-uma-bem-grande-e-secreta-123}
CUSTOMER_ID=${CUSTOMER_ID:-$((RANDOM + 1000))}
OWNER_ID=99                                       # dono do restaurante nos dados do profile stub
PRODUCT_ID=00000000-0000-0000-0000-0000000000b1   # X-Burger do profile stub
CORRELATION_ID="e2e-$(date +%s)"

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
token() { # $1 = id, $2 = role
  local header payload signature
  header=$(printf '{"alg":"HS256","typ":"JWT"}' | b64url)
  payload=$(printf '{"sub":"%s","role":"%s","exp":%s}' "$1" "$2" $(( $(date +%s) + 600 )) | b64url)
  signature=$(printf '%s.%s' "$header" "$payload" | openssl dgst -binary -sha256 -hmac "$JWT_SECRET" | b64url)
  printf '%s.%s.%s' "$header" "$payload" "$signature"
}
json_field() { python3 -c "import sys, json; print(json.load(sys.stdin)$1)"; }

CUSTOMER=$(token "$CUSTOMER_ID" CUSTOMER)
OWNER=$(token "$OWNER_ID" RESTAURANT_OWNER)
ADMIN=$(token 1 ADMIN)

echo "==> cliente $CUSTOMER_ID adiciona item ao carrinho"
curl -fsS -X POST "$ORDER_URL/cart/items" -H "Authorization: Bearer $CUSTOMER" \
  -H 'Content-Type: application/json' -d "{\"productId\":\"$PRODUCT_ID\",\"quantity\":2}" > /dev/null

echo "==> checkout (X-Correlation-ID: $CORRELATION_ID)"
ORDER_ID=$(curl -fsS -X POST "$ORDER_URL/orders/checkout" -H "Authorization: Bearer $CUSTOMER" \
  -H "X-Correlation-ID: $CORRELATION_ID" -H 'Content-Type: application/json' \
  -d '{"paymentMethod":"PIX"}' | json_field "['id']")
echo "    pedido $ORDER_ID criado em PENDING_PAYMENT"

STATUS=PENDING_PAYMENT
for _ in $(seq 1 30); do
  STATUS=$(curl -fsS "$ORDER_URL/orders/$ORDER_ID" -H "Authorization: Bearer $CUSTOMER" | json_field "['status']")
  [ "$STATUS" != PENDING_PAYMENT ] && break
  sleep 1
done
echo "    status do pedido após o pagamento: $STATUS"

PAYMENT=$(curl -fsS "$PAYMENT_URL/payments/order/$ORDER_ID" -H "Authorization: Bearer $CUSTOMER")
echo "    pagamento: $(echo "$PAYMENT" | json_field "['status']") (id $(echo "$PAYMENT" | json_field "['id']"))"

case "$STATUS" in
  PAYMENT_REJECTED)
    echo "==> pagamento recusado pelo simulador (PAYMENT_APPROVAL_RATE). Rode de novo para ver o estorno."
    exit 0 ;;
  PENDING_RESTAURANT) ;;
  *) echo "ERRO: status inesperado $STATUS" >&2; exit 1 ;;
esac

echo "==> restaurante rejeita o pedido (dispara RefundRequested)"
curl -fsS -X PATCH "$ORDER_URL/orders/$ORDER_ID/reject" -H "Authorization: Bearer $OWNER" \
  -H "X-Correlation-ID: $CORRELATION_ID" -H 'Content-Type: application/json' \
  -d '{"reason":"Sem entregador"}' > /dev/null

PAYMENT_STATUS=APPROVED
for _ in $(seq 1 30); do
  PAYMENT_STATUS=$(curl -fsS "$PAYMENT_URL/payments/order/$ORDER_ID" -H "Authorization: Bearer $ADMIN" | json_field "['status']")
  [ "$PAYMENT_STATUS" = REFUNDED ] && break
  sleep 1
done
echo "    pagamento após o estorno: $PAYMENT_STATUS"
[ "$PAYMENT_STATUS" = REFUNDED ] || { echo "ERRO: estorno não aconteceu" >&2; exit 1; }

echo "==> OK. Procure '$CORRELATION_ID' nos logs: docker compose logs order-service payment-service | grep $CORRELATION_ID"
