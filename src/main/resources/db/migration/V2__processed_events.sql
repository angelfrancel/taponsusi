CREATE TABLE processed_events (
                                  id                  BIGSERIAL PRIMARY KEY,
                                  platform            VARCHAR(50) NOT NULL,
                                  platform_event_id   VARCHAR(255) NOT NULL,
                                  received_at         TIMESTAMP NOT NULL DEFAULT now(),
                                  CONSTRAINT uq_platform_event UNIQUE (platform, platform_event_id)
);
