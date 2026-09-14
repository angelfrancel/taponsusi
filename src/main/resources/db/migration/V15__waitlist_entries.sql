CREATE TABLE waitlist_entries (
                                  id                  BIGSERIAL PRIMARY KEY,
                                  seller_id           BIGINT NOT NULL REFERENCES sellers(id),
                                  session_id          BIGINT NOT NULL REFERENCES live_sessions(id),
                                  inventory_item_id   BIGINT NOT NULL REFERENCES inventory_items(id),

                                  platform            VARCHAR(30) NOT NULL,
                                  platform_event_id   VARCHAR(255) NOT NULL,
                                  buyer_platform_id   VARCHAR(255) NOT NULL,

                                  requested_quantity  INTEGER NOT NULL,
                                  source_created_at   TIMESTAMPTZ NOT NULL,
                                  received_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

                                  status              VARCHAR(30) NOT NULL DEFAULT 'WAITING',
                                  offered_at          TIMESTAMPTZ,
                                  offer_expires_at    TIMESTAMPTZ,
                                  promoted_order_id   BIGINT REFERENCES orders(id),

                                  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
                                  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

                                  CONSTRAINT chk_waitlist_requested_quantity
                                      CHECK (requested_quantity > 0),

                                  CONSTRAINT chk_waitlist_status
                                      CHECK (
                                          status IN (
                                                     'WAITING',
                                                     'OFFERED',
                                                     'PROMOTED',
                                                     'DECLINED',
                                                     'EXPIRED',
                                                     'CANCELLED'
                                              )
                                          ),

                                  CONSTRAINT uq_waitlist_platform_event
                                      UNIQUE (platform, platform_event_id)
);

CREATE INDEX idx_waitlist_promotion_order
    ON waitlist_entries (
                         seller_id,
                         inventory_item_id,
                         status,
                         source_created_at,
                         id
        );

CREATE INDEX idx_waitlist_session
    ON waitlist_entries (seller_id, session_id);

CREATE INDEX idx_waitlist_buyer
    ON waitlist_entries (
                         seller_id,
                         session_id,
                         buyer_platform_id
        );

ALTER TABLE waitlist_entries ENABLE ROW LEVEL SECURITY;

CREATE POLICY waitlist_entries_tenant_isolation
    ON waitlist_entries
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

ALTER TABLE waitlist_entries FORCE ROW LEVEL SECURITY;