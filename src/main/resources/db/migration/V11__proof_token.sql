ALTER TABLE reservation_proofs ADD COLUMN proof_token VARCHAR(20);

CREATE INDEX idx_reservation_proofs_seller_token ON reservation_proofs(seller_id, proof_token);