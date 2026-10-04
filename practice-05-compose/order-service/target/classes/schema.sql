CREATE TABLE IF NOT EXISTS orders(id BIGINT PRIMARY KEY, product VARCHAR(120), quantity INT, total DECIMAL(14,2), address VARCHAR(240));
CREATE SEQUENCE IF NOT EXISTS order_seq START WITH 102;
INSERT INTO orders(id,product,quantity,total,address) SELECT 101,'SSD 1TB',8,63200.00,'Москва, ул. Академическая, 12' WHERE NOT EXISTS (SELECT 1 FROM orders WHERE id=101);
