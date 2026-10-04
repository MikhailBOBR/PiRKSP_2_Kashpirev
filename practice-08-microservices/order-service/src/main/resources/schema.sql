CREATE TABLE IF NOT EXISTS orders(id VARCHAR(40) PRIMARY KEY,product_id BIGINT,quantity INT,address VARCHAR(240),total DECIMAL(14,2),status VARCHAR(30),request_id VARCHAR(80),created_at VARCHAR(40));
CREATE TABLE IF NOT EXISTS outbox(event_id VARCHAR(40) PRIMARY KEY,order_id VARCHAR(40),product_id BIGINT,quantity INT,address VARCHAR(240),request_id VARCHAR(80),sent BOOLEAN);
