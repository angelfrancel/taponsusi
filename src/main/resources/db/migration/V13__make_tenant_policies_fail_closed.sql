-- When no tenant context exists, return no tenant-owned rows instead
-- of raising an SQL error. An empty or missing context must fail closed.

DROP POLICY live_sessions_tenant_isolation ON live_sessions;
CREATE POLICY live_sessions_tenant_isolation ON live_sessions
    USING (
        seller_id = NULLIF(
            current_setting('app.current_seller_id', true),
            ''
        )::BIGINT
    );

DROP POLICY inventory_items_tenant_isolation ON inventory_items;
CREATE POLICY inventory_items_tenant_isolation ON inventory_items
    USING (
        seller_id = NULLIF(
            current_setting('app.current_seller_id', true),
            ''
        )::BIGINT
    );

DROP POLICY orders_tenant_isolation ON orders;
CREATE POLICY orders_tenant_isolation ON orders
    USING (
        seller_id = NULLIF(
            current_setting('app.current_seller_id', true),
            ''
        )::BIGINT
    );

DROP POLICY audit_events_tenant_isolation ON audit_events;
CREATE POLICY audit_events_tenant_isolation ON audit_events
    USING (
        seller_id = NULLIF(
            current_setting('app.current_seller_id', true),
            ''
        )::BIGINT
    );

DROP POLICY reservation_proofs_tenant_isolation ON reservation_proofs;
CREATE POLICY reservation_proofs_tenant_isolation ON reservation_proofs
    USING (
        seller_id = NULLIF(
            current_setting('app.current_seller_id', true),
            ''
        )::BIGINT
    );

DROP POLICY buyer_reputation_tenant_isolation ON buyer_reputation;
CREATE POLICY buyer_reputation_tenant_isolation ON buyer_reputation
    USING (
        seller_id = NULLIF(
            current_setting('app.current_seller_id', true),
            ''
        )::BIGINT
    );