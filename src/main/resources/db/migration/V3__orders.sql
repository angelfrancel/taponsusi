CREATE TABLE orders (
                        id                  BIGSERIAL PRIMARY KEY,
                        seller_id           BIGINT NOT NULL REFERENCES sellers(id),
                        session_id          BIGINT NOT NULL REFERENCES live_sessions(id),
                        inventory_item_id   BIGINT NOT NULL REFERENCES inventory_items(id),
                        buyer_platform_id   VARCHAR(255) NOT NULL,
                        requested_quantity  INTEGER NOT NULL,
                        quantity            INTEGER NOT NULL,  -- fulfilled amount (Section 5: partial fulfillment)
                        status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
                        created_at          TIMESTAMP NOT NULL DEFAULT now(),
                        updated_at          TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_seller ON orders(seller_id);
CREATE INDEX idx_orders_inventory_item ON orders(inventory_item_id);
CREATE INDEX idx_orders_buyer ON orders(seller_id, buyer_platform_id);

ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
CREATE POLICY orders_tenant_isolation ON orders
    USING (seller_id = current_setting('app.current_seller_id')::BIGINT);