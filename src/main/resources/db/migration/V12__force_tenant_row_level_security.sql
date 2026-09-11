-- ENABLE ROW LEVEL SECURITY alone does not restrict the table owner.
-- FORCE ensures the application role must also obey tenant policies.

ALTER TABLE live_sessions FORCE ROW LEVEL SECURITY;
ALTER TABLE inventory_items FORCE ROW LEVEL SECURITY;
ALTER TABLE orders FORCE ROW LEVEL SECURITY;
ALTER TABLE audit_events FORCE ROW LEVEL SECURITY;
ALTER TABLE reservation_proofs FORCE ROW LEVEL SECURITY;
ALTER TABLE buyer_reputation FORCE ROW LEVEL SECURITY;