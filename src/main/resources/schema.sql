CREATE TABLE IF NOT EXISTS product (
                                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                       sku VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    price DECIMAL(12,2) NOT NULL CHECK (price >= 0)
    );

CREATE TABLE IF NOT EXISTS warehouse (
                                         id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                         code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL
    );

CREATE TABLE IF NOT EXISTS stock_level (
                                           id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                           warehouse_id BIGINT NOT NULL REFERENCES warehouse(id),
    product_id BIGINT NOT NULL REFERENCES product(id),
    on_hand INT NOT NULL,
    reserved INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_stock UNIQUE (warehouse_id, product_id),
    CONSTRAINT chk_on_hand CHECK (on_hand >= 0),
    CONSTRAINT chk_reserved CHECK (reserved >= 0 AND reserved <= on_hand)
    );

CREATE TABLE IF NOT EXISTS orders (
                                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                      customer_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    idempotency_key VARCHAR(64) UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
                             version BIGINT NOT NULL DEFAULT 0
                             );

CREATE INDEX IF NOT EXISTS idx_orders_status_expiry
    ON orders(status, expires_at);

CREATE INDEX IF NOT EXISTS idx_orders_customer
    ON orders(customer_id);

CREATE TABLE IF NOT EXISTS order_line (
                                          id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                          order_id BIGINT NOT NULL REFERENCES orders(id),
    product_id BIGINT NOT NULL REFERENCES product(id),
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price DECIMAL(12,2) NOT NULL
    );

CREATE TABLE IF NOT EXISTS reservation (
                                           id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                           order_id BIGINT NOT NULL REFERENCES orders(id),
    stock_level_id BIGINT NOT NULL REFERENCES stock_level(id),
    quantity INT NOT NULL CHECK (quantity > 0),
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
                             );

CREATE INDEX IF NOT EXISTS idx_res_order
    ON reservation(order_id);