CREATE TABLE products (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    price NUMERIC(10, 2) NOT NULL CHECK (price > 0),
    image_url VARCHAR(500),
    available BOOLEAN NOT NULL DEFAULT TRUE,
    restaurant_id UUID NOT NULL,
    category_id UUID NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_products_category
        FOREIGN KEY (category_id) REFERENCES categories (id)
);

CREATE INDEX idx_products_restaurant_id
    ON products (restaurant_id);

CREATE INDEX idx_products_category_id
    ON products (category_id);

CREATE INDEX idx_products_restaurant_available
    ON products (restaurant_id, available);
