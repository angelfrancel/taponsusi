ALTER TABLE orders ADD COLUMN expires_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN overdue_since TIMESTAMP;

CREATE INDEX idx_orders_expires_at ON orders(expires_at) WHERE status = 'PENDING';
CREATE INDEX idx_orders_overdue_since ON orders(overdue_since) WHERE status = 'OVERDUE';