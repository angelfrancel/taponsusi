ALTER TABLE waitlist_entries
    ADD COLUMN offered_quantity INTEGER;

ALTER TABLE waitlist_entries
    ADD CONSTRAINT chk_waitlist_offered_quantity
        CHECK (
            offered_quantity IS NULL
                OR offered_quantity > 0
            );