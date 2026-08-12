ALTER TABLE live_sessions ADD COLUMN facebook_page_id VARCHAR(255);


CREATE INDEX idx_live_sessions_facebook_page_id ON live_sessions(facebook_page_id);