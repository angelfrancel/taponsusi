CREATE TABLE reservation_proofs (
                                    id                  BIGSERIAL PRIMARY KEY,
                                    seller_id           BIGINT NOT NULL REFERENCES sellers(id),
                                    order_id            BIGINT NOT NULL REFERENCES orders(id),
                                    sequence_number     BIGINT NOT NULL,
                                    proof_data          TEXT NOT NULL,
                                    previous_hash       VARCHAR(64) NOT NULL,
                                    hash                VARCHAR(64) NOT NULL,
                                    created_at          TIMESTAMP NOT NULL DEFAULT now(),
                                    CONSTRAINT uq_proof_seller_sequence UNIQUE (seller_id, sequence_number)
);

CREATE INDEX idx_reservation_proofs_seller ON reservation_proofs(seller_id);
CREATE INDEX idx_reservation_proofs_order ON reservation_proofs(order_id);

ALTER TABLE reservation_proofs ENABLE ROW LEVEL SECURITY;
CREATE POLICY reservation_proofs_tenant_isolation ON reservation_proofs
    USING (seller_id = current_setting('app.current_seller_id')::BIGINT);