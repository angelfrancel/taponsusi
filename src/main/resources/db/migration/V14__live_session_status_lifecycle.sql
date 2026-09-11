-- Flyway currently uses the table-owner application role.
-- Temporarily allow the owner to perform this controlled data migration.
ALTER TABLE live_sessions NO FORCE ROW LEVEL SECURITY;

-- Map the previous session model to the new lifecycle.
UPDATE live_sessions
SET status = 'DRAFT'
WHERE status = 'SCHEDULED';

ALTER TABLE live_sessions
    ALTER COLUMN status SET DEFAULT 'DRAFT';

ALTER TABLE live_sessions
    ADD CONSTRAINT chk_live_sessions_status
        CHECK (
            status IN (
                       'DRAFT',
                       'READY',
                       'LIVE',
                       'PAUSED',
                       'ENDED'
                )
            );

-- Restore mandatory tenant isolation before the migration commits.
ALTER TABLE live_sessions FORCE ROW LEVEL SECURITY;