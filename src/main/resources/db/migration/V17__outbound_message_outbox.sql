CREATE TABLE outbound_messages (
                                   id                  BIGSERIAL PRIMARY KEY,
                                   seller_id           BIGINT NOT NULL REFERENCES sellers(id),

                                   message_type        VARCHAR(50) NOT NULL,
                                   idempotency_key     VARCHAR(255) NOT NULL,

                                   platform            VARCHAR(30) NOT NULL,
                                   buyer_platform_id   VARCHAR(255) NOT NULL,
                                   message_text        TEXT NOT NULL,

                                   status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
                                   attempt_count       INTEGER NOT NULL DEFAULT 0,
                                   last_error          VARCHAR(1000),

                                   created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
                                   updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
                                   sent_at             TIMESTAMPTZ,

                                   CONSTRAINT uq_outbound_message_idempotency
                                       UNIQUE (idempotency_key),

                                   CONSTRAINT chk_outbound_message_status
                                       CHECK (
                                           status IN (
                                                      'PENDING',
                                                      'SENT',
                                                      'FAILED'
                                               )
                                           ),

                                   CONSTRAINT chk_outbound_message_attempt_count
                                       CHECK (attempt_count >= 0)
);

CREATE INDEX idx_outbound_messages_pending
    ON outbound_messages (
                          status,
                          created_at,
                          id
        );

CREATE INDEX idx_outbound_messages_seller
    ON outbound_messages (
                          seller_id,
                          created_at,
                          id
        );

ALTER TABLE outbound_messages
    ENABLE ROW LEVEL SECURITY;

CREATE POLICY outbound_messages_tenant_isolation
    ON outbound_messages
    USING (
        seller_id = NULLIF(
            current_setting(
                'app.current_seller_id',
                true
            ),
            ''
        )::BIGINT
    )
    WITH CHECK (
        seller_id = NULLIF(
            current_setting(
                'app.current_seller_id',
                true
            ),
            ''
        )::BIGINT
    );

ALTER TABLE outbound_messages
    FORCE ROW LEVEL SECURITY;