CREATE TABLE sellers (
                         id              BIGSERIAL PRIMARY KEY,
                         email           VARCHAR(255) NOT NULL UNIQUE,
                         password_hash   VARCHAR(255) NOT NULL,
                         business_name   VARCHAR(255) NOT NULL,
                         created_at      TIMESTAMP NOT NULL DEFAULT now(),
                         updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE live_sessions (
                               id              BIGSERIAL PRIMARY KEY,
                               seller_id       BIGINT NOT NULL REFERENCES sellers(id),
                               title           VARCHAR(255) NOT NULL,
                               platform        VARCHAR(50) NOT NULL DEFAULT 'SIMULATION',
                               status          VARCHAR(50) NOT NULL DEFAULT 'SCHEDULED',
                               started_at      TIMESTAMP,
                               ended_at        TIMESTAMP,
                               created_at      TIMESTAMP NOT NULL DEFAULT now(),
                               updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE inventory_items (
                                 id                  BIGSERIAL PRIMARY KEY,
                                 seller_id           BIGINT NOT NULL REFERENCES sellers(id),
                                 session_id          BIGINT NOT NULL REFERENCES live_sessions(id),
                                 reservation_code    VARCHAR(100) NOT NULL,
                                 name                VARCHAR(255) NOT NULL,
                                 total_stock         INTEGER NOT NULL CHECK (total_stock >= 0),
                                 available_stock     INTEGER NOT NULL CHECK (available_stock >= 0),
                                 max_quantity_per_buyer INTEGER NOT NULL DEFAULT 10,
                                 created_at          TIMESTAMP NOT NULL DEFAULT now(),
                                 updated_at          TIMESTAMP NOT NULL DEFAULT now(),

                                 CONSTRAINT uq_inventory_code_per_session UNIQUE (seller_id, session_id, reservation_code)
);

CREATE INDEX idx_live_sessions_seller ON live_sessions(seller_id);
CREATE INDEX idx_inventory_seller ON inventory_items(seller_id);
CREATE INDEX idx_inventory_session ON inventory_items(session_id);

ALTER TABLE live_sessions ENABLE ROW LEVEL SECURITY;
CREATE POLICY live_sessions_tenant_isolation ON live_sessions
    USING (seller_id = current_setting('app.current_seller_id')::BIGINT);

ALTER TABLE inventory_items ENABLE ROW LEVEL SECURITY;
CREATE POLICY inventory_items_tenant_isolation ON inventory_items
    USING (seller_id = current_setting('app.current_seller_id')::BIGINT);