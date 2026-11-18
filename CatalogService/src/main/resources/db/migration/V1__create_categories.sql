CREATE TABLE categories (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    restaurant_id UUID NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
);

CREATE INDEX idx_categories_restaurant_id
    ON categories (restaurant_id);

-- Evita nomes duplicados ignorando maiúsculas/minúsculas entre categorias ativas.
CREATE UNIQUE INDEX uq_categories_restaurant_name_active
    ON categories (restaurant_id, LOWER(name))
    WHERE active = TRUE;
