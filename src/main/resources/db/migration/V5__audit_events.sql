CREATE TABLE audit_events (
                              id              BIGSERIAL PRIMARY KEY,
                              seller_id       BIGINT NOT NULL REFERENCES sellers(id),
                              event_type      VARCHAR(100) NOT NULL,
                              entity_type     VARCHAR(50) NOT NULL,
                              entity_id       BIGINT NOT NULL,
                              detail          TEXT,
                              created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_events_seller ON audit_events(seller_id);
CREATE INDEX idx_audit_events_entity ON audit_events(entity_type, entity_id);

ALTER TABLE audit_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY audit_events_tenant_isolation ON audit_events
    USING (seller_id = current_setting('app.current_seller_id')::BIGINT);