CREATE TABLE buyer_reputation (
                                  id                          BIGSERIAL PRIMARY KEY,
                                  seller_id                   BIGINT NOT NULL REFERENCES sellers(id),
                                  buyer_platform_id           VARCHAR(255) NOT NULL,
                                  total_orders                INTEGER NOT NULL DEFAULT 0,
                                  orders_paid_on_time         INTEGER NOT NULL DEFAULT 0,
                                  orders_overdue              INTEGER NOT NULL DEFAULT 0,
                                  orders_cancelled_by_seller  INTEGER NOT NULL DEFAULT 0,
                                  flagged_as                  VARCHAR(20) NOT NULL DEFAULT 'NONE',
                                  notes                       TEXT,
                                  last_updated                TIMESTAMP NOT NULL DEFAULT now(),
                                  CONSTRAINT uq_buyer_reputation_seller_buyer UNIQUE (seller_id, buyer_platform_id)
);

CREATE INDEX idx_buyer_reputation_seller ON buyer_reputation(seller_id);

ALTER TABLE buyer_reputation ENABLE ROW LEVEL SECURITY;
CREATE POLICY buyer_reputation_tenant_isolation ON buyer_reputation
    USING (seller_id = current_setting('app.current_seller_id')::BIGINT);