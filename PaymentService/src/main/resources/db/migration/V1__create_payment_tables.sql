CREATE TABLE payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    customer_id BIGINT NOT NULL,
    amount NUMERIC(10, 2) NOT NULL CHECK (amount > 0),
    method VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    -- Um pagamento por pedido: é o que torna o PaymentRequested idempotente.
    CONSTRAINT uk_payments_order_id UNIQUE (order_id)
);

CREATE INDEX idx_payments_customer_id
    ON payments (customer_id, created_at);

CREATE TABLE payment_transactions (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    amount NUMERIC(10, 2) NOT NULL,
    reason VARCHAR(255),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_payment_transactions_payment
        FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE CASCADE
);

CREATE INDEX idx_payment_transactions_payment_id
    ON payment_transactions (payment_id, created_at);
