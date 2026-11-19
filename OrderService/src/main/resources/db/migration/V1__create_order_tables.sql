CREATE TABLE carts (
    id UUID PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    restaurant_id UUID,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT uk_carts_customer_id UNIQUE (customer_id)
);

CREATE TABLE cart_items (
    id UUID PRIMARY KEY,
    cart_id UUID NOT NULL,
    product_id UUID NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    unit_price NUMERIC(10, 2) NOT NULL CHECK (unit_price > 0),
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 99),
    CONSTRAINT fk_cart_items_cart
        FOREIGN KEY (cart_id) REFERENCES carts (id) ON DELETE CASCADE
);

CREATE INDEX idx_cart_items_cart_id
    ON cart_items (cart_id);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    restaurant_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    total NUMERIC(10, 2) NOT NULL CHECK (total >= 0),
    rejection_reason VARCHAR(255),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE
);

CREATE INDEX idx_orders_customer_id
    ON orders (customer_id, created_at);

CREATE INDEX idx_orders_restaurant_status
    ON orders (restaurant_id, status, created_at);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    product_id UUID NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    unit_price NUMERIC(10, 2) NOT NULL CHECK (unit_price > 0),
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 99),
    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
);

CREATE INDEX idx_order_items_order_id
    ON order_items (order_id);
