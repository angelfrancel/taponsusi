CREATE TABLE password_reset_tokens (
                                       id          BIGSERIAL PRIMARY KEY,
                                       seller_id   BIGINT NOT NULL REFERENCES sellers(id),
                                       token       VARCHAR(255) NOT NULL UNIQUE,
                                       expires_at  TIMESTAMP NOT NULL,
                                       used        BOOLEAN NOT NULL DEFAULT FALSE,
                                       created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_password_reset_tokens_seller ON password_reset_tokens(seller_id);